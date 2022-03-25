/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.ir2wasm

import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.utils.DiscriminatedUnions
import org.jetbrains.kotlin.backend.wasm.utils.getWasmArrayAnnotation
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.isAnnotationClass
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

class WasmModuleFragmentGenerator(
    backendContext: WasmBackendContext,
    wasmModuleFragment: WasmCompiledModuleFragment,
    allowIncompleteImplementations: Boolean,
) {
    private val hierarchyIntersectedUnions = DiscriminatedUnions<IrClass>()

    private val declarationGenerator =
        DeclarationGenerator(
            WasmModuleCodegenContextImpl(
                backendContext,
                wasmModuleFragment,
            ),
            allowIncompleteImplementations,
            hierarchyIntersectedUnions,
        )

    private val interfaceCollector = object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) { }

        override fun visitClass(declaration: IrClass) {
            if (declaration.isAnnotationClass) return
            if (declaration.isExternal) return
            if (declaration.getWasmArrayAnnotation() != null) return
            if (declaration.isInterface) return
            val symbol = declaration.symbol

            if (declaration.modality != Modality.ABSTRACT) {
                val classMetadata = declarationGenerator.context.getClassMetadata(symbol)
                if (classMetadata.interfaces.isNotEmpty()) {
                    hierarchyIntersectedUnions.addUnion(classMetadata.interfaces)
                }
            }
        }
    }

    fun collectInterfaceTables(irModuleFragment: IrModuleFragment) {
        acceptVisitor(irModuleFragment, interfaceCollector)
        hierarchyIntersectedUnions.compress()
    }

    fun generateModule(irModuleFragment: IrModuleFragment) {
        acceptVisitor(irModuleFragment, declarationGenerator)
    }

    private fun acceptVisitor(irModuleFragment: IrModuleFragment, visitor: IrElementVisitorVoid) {
        for (irFile in irModuleFragment.files) {
            for (irDeclaration in irFile.declarations) {
                irDeclaration.acceptVoid(visitor)
            }
        }
    }
}
