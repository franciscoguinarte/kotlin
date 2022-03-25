/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.wasm.ir

class WasmIrExpressionBuilder(
    val expression: MutableList<WasmInstr>
) : WasmExpressionBuilder() {

    override fun buildInstr(instr: WasmInstr) {
        val foldedInstrs = foldWasmInstructions(expression.lastOrNull(), instr)

        if (foldedInstrs == null) {
            expression += instr
        } else {
            expression.removeLastOrNull()
            expression += foldedInstrs
        }
    }

    override fun buildInstr(op: WasmOp, vararg immediates: WasmImmediate) {
        buildInstr(WasmInstr(op, immediates.toList()))
    }

    override var numberOfNestedBlocks: Int = 0
        set(value) {
            assert(value >= 0) { "end without matching block" }
            field = value
        }
}

inline fun buildWasmExpression(body: WasmExpressionBuilder.() -> Unit): MutableList<WasmInstr> {
    val res = mutableListOf<WasmInstr>()
    WasmIrExpressionBuilder(res).body()
    return res
}
