/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.transformers.irToJs

import org.jetbrains.kotlin.ir.backend.js.utils.sanitizeName
import org.jetbrains.kotlin.js.backend.ast.*

class JsIrProgramFragment(val packageFqn: String) {
    val nameBindings = mutableMapOf<String, JsName>()
    val declarations = JsGlobalBlock()
    val exports = JsGlobalBlock()
    val importedModules = mutableListOf<JsImportedModule>()
    val imports = mutableMapOf<String, JsExpression>()
    var dts: String? = null
    val classes = mutableMapOf<JsName, JsIrIcClassModel>()
    val initializers = JsGlobalBlock()
    var mainFunction: JsStatement? = null
    var testFunInvocation: JsStatement? = null
    var suiteFn: JsName? = null
    val definitions = mutableSetOf<String>()
    val polyfills = JsGlobalBlock()
}

class JsIrModule(
    val moduleName: String,
    val externalModuleName: String,
    val fragments: List<JsIrProgramFragment>
) {
    fun makeModuleHeader(): JsIrModuleHeader {
        val nameBindings = mutableMapOf<String, String>()
        val definitions = mutableSetOf<String>()
        var hasJsExports = false
        for (fragment in fragments) {
            hasJsExports = hasJsExports || !fragment.exports.isEmpty
            for ((tag, name) in fragment.nameBindings.entries) {
                nameBindings[tag] = name.toString()
            }
            definitions += fragment.definitions
        }
        return JsIrModuleHeader(moduleName, externalModuleName, definitions, nameBindings, hasJsExports, this)
    }
}

class JsIrModuleHeader(
    val moduleName: String,
    val externalModuleName: String,
    val definitions: Set<String>,
    val nameBindings: Map<String, String>,
    val hasJsExports: Boolean,
    val associatedModule: JsIrModule?
) {
    val externalNames: Set<String> by lazy { nameBindings.keys - definitions }
}

class JsIrProgram(val modules: List<JsIrModule>) {
    val mainModule = modules.last()
    val otherModules = modules.dropLast(1)

    fun crossModuleDependencies(relativeRequirePath: Boolean): Map<JsIrModule, CrossModuleReferences> {
        val resolver = CrossModuleDependenciesResolver(modules.map { it.makeModuleHeader() })
        return resolver.resolveCrossModuleDependencies(relativeRequirePath)
    }
}

class CrossModuleDependenciesResolver(private val headers: List<JsIrModuleHeader>) {
    fun resolveCrossModuleDependencies(relativeRequirePath: Boolean): Map<JsIrModule, CrossModuleReferences> {
        val headerToBuilder = headers.associateWith { JsIrModuleCrossModuleReferecenceBuilder(it, relativeRequirePath) }
        val definitionModule = mutableMapOf<String, JsIrModuleCrossModuleReferecenceBuilder>()

        val mainModuleHeader = headers.last()
        val otherModuleHeaders = headers.dropLast(1)
        headerToBuilder[mainModuleHeader]!!.transitiveJsExportFrom = otherModuleHeaders

        for (header in headers) {
            val builder = headerToBuilder[header]!!
            for (definition in header.definitions) {
                require(definition !in definitionModule) { "Duplicate definition: $definition" }
                definitionModule[definition] = builder
            }
        }

        for (header in headers) {
            val builder = headerToBuilder[header]!!
            for (tag in header.externalNames) {
                val fromModuleBuilder = definitionModule[tag] ?: continue // TODO error?

                builder.imports += CrossModuleRef(fromModuleBuilder, tag)
                fromModuleBuilder.exports += tag
            }
        }

        val result = mutableMapOf<JsIrModule, CrossModuleReferences>()
        for (header in headers) {
            val builder = headerToBuilder[header]!!
            builder.buildExportNames()
            if (header.associatedModule != null) {
                result[header.associatedModule] = builder.buildCrossModuleRefs()
            }
        }

        return result
    }
}

private class CrossModuleRef(val module: JsIrModuleCrossModuleReferecenceBuilder, val tag: String)

private class JsIrModuleCrossModuleReferecenceBuilder(val header: JsIrModuleHeader, val relativeRequirePath: Boolean) {
    val imports = mutableListOf<CrossModuleRef>()
    val exports = mutableSetOf<String>()
    var transitiveJsExportFrom = emptyList<JsIrModuleHeader>()

    private lateinit var exportNames: Map<String, String> // tag -> name

    fun buildExportNames() {
        val names = header.nameBindings.entries.associate { it.key to sanitizeName(it.value) }
        val nameToCnt = mutableMapOf<String, Int>()

        exportNames = exports.sorted().associateWith { tag ->
            val suggestedName = names[tag] ?: error("Name not found for tag $tag")
            val suffix = nameToCnt[suggestedName]?.let { "_$it" } ?: ""
            nameToCnt[suggestedName] = (nameToCnt[suggestedName] ?: 0) + 1
            suggestedName + suffix
        }
    }

    fun buildCrossModuleRefs(): CrossModuleReferences {
        val module = header.associatedModule ?: error("Internal error: associated module is null")

        val importedModules = mutableMapOf<JsIrModuleHeader, JsImportedModule>()

        fun import(moduleHeader: JsIrModuleHeader): JsName {
            return importedModules.getOrPut(moduleHeader) {
                val jsModuleName = JsName(moduleHeader.moduleName, false)
                JsImportedModule(moduleHeader.externalModuleName, jsModuleName, null, relativeRequirePath)
            }.internalName
        }

        val tagToName = module.fragments.flatMap { it.nameBindings.entries }.associate { it.key to it.value }

        val resultImports = imports.associate { crossModuleRef ->
            val tag = crossModuleRef.tag
            require(crossModuleRef.module::exportNames.isInitialized) {
                // This situation appears in case of a dependent module redefine a symbol (function) from their dependency
                "Cross module dependency resolution failed due to symbol '${tag.takeWhile { c -> c != '|' }}' redefinition"
            }
            val exportedAs = crossModuleRef.module.exportNames[tag]!!
            val importedAs = tagToName[tag]!!
            val moduleName = import(crossModuleRef.module.header)

            val importStatement = JsVars.JsVar(importedAs, JsNameRef(exportedAs, ReservedJsNames.makeCrossModuleNameRef(moduleName)))

            tag to importStatement
        }

        val transitiveExport = transitiveJsExportFrom.mapNotNull {
            if (it.hasJsExports) import(it) else null
        }
        return CrossModuleReferences(importedModules.values.toList(), resultImports, exportNames, transitiveExport)
    }
}

class CrossModuleReferences(
    val importedModules: List<JsImportedModule>, // additional Kotlin imported modules
    val imports: Map<String, JsVars.JsVar>, // tag -> import statement
    val exports: Map<String, String>, // tag -> name
    val transitiveJsExportFrom: List<JsName> // the list of modules which provide their js exports for transitive export
) {
    companion object {
        val Empty = CrossModuleReferences(listOf(), emptyMap(), emptyMap(), emptyList())
    }
}
