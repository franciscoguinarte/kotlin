/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.ic

import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.JsIrProgramFragment
import java.io.File

class SrcFileArtifact(val srcFilePath: String, val fragment: JsIrProgramFragment?, astArtifactFilePath: String) {
    class Artifact(private val artifactFilePath: String) {
        fun fetchData() = File(artifactFilePath).ifExists { readBytes() }
    }

    val astFileArtifact = Artifact(astArtifactFilePath)
}

class KLibArtifact(val moduleName: String, val fileArtifacts: List<SrcFileArtifact>)

abstract class ArtifactCache {
    protected val binaryAsts = mutableMapOf<String, ByteArray>()
    protected val fragments = mutableMapOf<String, JsIrProgramFragment>()

    fun saveBinaryAst(srcPath: String, binaryAst: ByteArray) {
        binaryAsts[srcPath] = binaryAst
    }

    fun saveFragment(srcPath: String, fragment: JsIrProgramFragment) {
        fragments[srcPath] = fragment
    }

    abstract fun fetchArtifacts(): KLibArtifact
}
