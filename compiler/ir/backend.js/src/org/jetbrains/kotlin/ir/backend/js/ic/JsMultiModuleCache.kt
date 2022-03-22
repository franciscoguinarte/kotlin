/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.backend.js.CompilationOutputs
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.JsIrModuleHeader
import java.io.File

class JsMultiModuleCache(private val moduleArtifacts: List<ModuleArtifact>) {
    companion object {
        private const val cacheModuleHeader = "cache.module.header.info"
        private const val cacheJsModuleFile = "cache.module.js"
        private const val cacheJsMapModuleFile = "cache.module.js.map"
    }

    private val moduleHeaders = mutableMapOf<ModuleArtifact, JsIrModuleHeader>()

    private enum class NameType(val typeMask: Int) {
        DEFINITIONS(0b01),
        NAME_BINDINGS(0b10)
    }

    private fun ModuleArtifact.fetchModuleHeader(): JsIrModuleHeader? {
        val header = File(artifactsDir, cacheModuleHeader).useCodedInputIfExists {
            val definitions = mutableSetOf<String>()
            val nameBindings = mutableMapOf<String, String>()

            val hasJsExports = readBool()
            val namesCount = readInt32()
            repeat(namesCount) {
                val tag = readString()
                val mask = readInt32()
                if (mask and NameType.DEFINITIONS.typeMask != 0) {
                    definitions += tag
                }
                if (mask and NameType.NAME_BINDINGS.typeMask != 0) {
                    nameBindings[tag] = readString()
                }
            }
            JsIrModuleHeader(moduleSafeName, moduleSafeName, definitions, nameBindings, hasJsExports, null)
        }
        if (header != null) {
            moduleHeaders[this] = header
        }
        return header
    }

    private fun ModuleArtifact.commitModuleHeader(header: JsIrModuleHeader) = artifactsDir?.let { cacheDir ->
        File(cacheDir, cacheModuleHeader).useCodedOutput {
            val names = mutableMapOf<String, Pair<Int, String?>>()
            for ((tag, name) in header.nameBindings) {
                names[tag] = NameType.NAME_BINDINGS.typeMask to name
            }
            for (tag in header.definitions) {
                val maskAndName = names[tag]
                names[tag] = ((maskAndName?.first ?: 0) or NameType.DEFINITIONS.typeMask) to maskAndName?.second
            }
            writeBoolNoTag(header.hasJsExports)
            writeInt32NoTag(names.size)
            for ((tag, maskAndName) in names) {
                writeStringNoTag(tag)
                writeInt32NoTag(maskAndName.first)
                if (maskAndName.second != null) {
                    writeStringNoTag(maskAndName.second)
                }
            }
        }
    }

    private fun ModuleArtifact.loadModuleAndSetHeader(): JsIrModuleHeader {
        val module = loadJsIrModule()
        val header = module.makeModuleHeader()
        moduleHeaders[this] = header
        return header
    }

    private fun JsIrModuleHeader.isModuleLoaded() = associatedModule != null

    private fun ModuleArtifact.fetchCompiledJsCode(): CompilationOutputs? {
        val jsCode = File(artifactsDir, cacheJsModuleFile).ifExists { readText() }
        val sourceMap = File(artifactsDir, cacheJsMapModuleFile).ifExists { readText() }
        return jsCode?.let { CompilationOutputs(it, null, sourceMap) }
    }

    fun commitCompiledJsCode(artifact: ModuleArtifact, compilationOutputs: CompilationOutputs) = artifact.artifactsDir?.let { cacheDir ->
        val jsCodeCache = File(cacheDir, cacheJsModuleFile).apply { recreate() }
        jsCodeCache.writeText(compilationOutputs.jsCode)
        val jsMapCache = File(cacheDir, cacheJsMapModuleFile)
        if (compilationOutputs.sourceMap != null) {
            jsMapCache.recreate()
            jsMapCache.writeText(compilationOutputs.sourceMap)
        } else {
            jsMapCache.ifExists { delete() }
        }
    }

    class CachedModule(val artifact: ModuleArtifact, val header: JsIrModuleHeader, val compilationOutputs: CompilationOutputs?)

    fun loadProgramFromCache(): List<CachedModule> {
        val updatedExternalNames = mutableSetOf<String>()
        val updatedDefinitions = mutableSetOf<String>()

        var mainModuleMustBeUpdated = false
        for (artifact in moduleArtifacts) {
            val cachedHeader = artifact.fetchModuleHeader()
            if (cachedHeader == null) {
                val actualHeader = artifact.loadModuleAndSetHeader()
                mainModuleMustBeUpdated = true
                updatedExternalNames += actualHeader.externalNames
                updatedDefinitions += actualHeader.definitions
                artifact.commitModuleHeader(actualHeader)
            } else if (artifact.fileArtifacts.any { it.isModified() }) {
                val actualHeader = artifact.loadModuleAndSetHeader()
                mainModuleMustBeUpdated = mainModuleMustBeUpdated || cachedHeader.hasJsExports != actualHeader.hasJsExports
                if (actualHeader.externalNames != cachedHeader.externalNames) {
                    updatedExternalNames += actualHeader.externalNames
                    updatedExternalNames += cachedHeader.externalNames
                }
                if (actualHeader.definitions != cachedHeader.definitions) {
                    updatedDefinitions += actualHeader.definitions
                    updatedDefinitions += cachedHeader.definitions
                }
                artifact.commitModuleHeader(actualHeader)
            }
        }

        if (mainModuleMustBeUpdated) {
            val mainModuleArtifact = moduleArtifacts.last()
            if (!moduleHeaders[mainModuleArtifact]!!.isModuleLoaded()) {
                val header = mainModuleArtifact.loadModuleAndSetHeader()
                updatedExternalNames += header.externalNames
                updatedDefinitions += header.definitions
            }
        }

        for (artifact in moduleArtifacts) {
            val header = moduleHeaders[artifact]!!
            // modules with the implicitly updated exports
            if (!header.isModuleLoaded() && header.definitions.any { it in updatedExternalNames }) {
                updatedDefinitions += artifact.loadModuleAndSetHeader().definitions
            }
        }

        for (artifact in moduleArtifacts) {
            val header = moduleHeaders[artifact]!!
            // modules with the implicitly updated imports
            if (!header.isModuleLoaded() && header.externalNames.any { it in updatedDefinitions }) {
                artifact.loadModuleAndSetHeader()
            }
        }

        // modules order matters
        return moduleArtifacts.map { artifact ->
            var header = moduleHeaders[artifact]!!
            var compilationOutputs: CompilationOutputs? = null
            if (!header.isModuleLoaded()) {
                compilationOutputs = artifact.fetchCompiledJsCode()
                if (compilationOutputs == null) {
                    header = artifact.loadModuleAndSetHeader()
                }
            }
            CachedModule(artifact, header, compilationOutputs)
        }
    }
}
