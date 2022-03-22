/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.backend.js.CompilationOutputs
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.*
import org.jetbrains.kotlin.serialization.js.ModuleKind

class JsExecutableProducer(
    private val mainModuleName: String,
    private val moduleKind: ModuleKind,
    private val sourceMapsInfo: SourceMapsInfo?,
    private val caches: List<ModuleArtifact>,
    private val relativeRequirePath: Boolean
) {
    fun buildExecutable(multiModule: Boolean) = if (multiModule) {
        buildMultiModuleExecutable()
    } else {
        buildSingleModuleExecutable()
    }

    private fun buildSingleModuleExecutable(): CompilationOutputs {
        val program = JsIrProgram(caches.map { cacheArtifact -> cacheArtifact.loadJsIrModule() })
        return generateSingleWrappedModuleBody(
            moduleName = mainModuleName,
            moduleKind = moduleKind,
            fragments = program.modules.flatMap { it.fragments },
            sourceMapsInfo = sourceMapsInfo,
            generateScriptModule = false,
            generateCallToMain = true,
        )
    }

    private fun buildMultiModuleExecutable(): CompilationOutputs {
        val jsMultiModuleCache = JsMultiModuleCache(caches)
        val cachedProgram = jsMultiModuleCache.loadProgramFromCache()

        val resolver = CrossModuleDependenciesResolver(cachedProgram.map { it.header })
        val crossModuleReferences = resolver.resolveCrossModuleDependencies(relativeRequirePath)

        val cachedMainModule = cachedProgram.last()
        val cachedOtherModules = cachedProgram.dropLast(1)

        fun JsMultiModuleCache.CachedModule.compileModule(moduleName: String, generateCallToMain: Boolean): CompilationOutputs {
            if (header.associatedModule != null) {
                val compiledModule = generateSingleWrappedModuleBody(
                    moduleName = moduleName,
                    moduleKind = moduleKind,
                    header.associatedModule.fragments,
                    sourceMapsInfo = sourceMapsInfo,
                    generateScriptModule = false,
                    generateCallToMain = generateCallToMain,
                    crossModuleReferences[header.associatedModule]
                        ?: error("Internal error: cannot find cross references for module $moduleName")
                )
                jsMultiModuleCache.commitCompiledJsCode(artifact, compiledModule)
                return compiledModule
            }
            return compilationOutputs ?: error("Internal error: cannot find cached output for module $moduleName")
        }

        val mainModule = cachedMainModule.compileModule(mainModuleName, true)
        val dependencies = cachedOtherModules.map {
            it.header.externalModuleName to it.compileModule(it.header.externalModuleName, false)
        }
        return CompilationOutputs(mainModule.jsCode, mainModule.jsProgram, mainModule.sourceMap, dependencies)
    }
}
