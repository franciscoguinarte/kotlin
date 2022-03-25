/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.utils

class DiscriminatedUnions<T> {
    private val leafParents = mutableMapOf<T, Node>()
    private var dirty: Boolean = false

    private inner class Node(var rang: Int = 0, val leafs: MutableList<T> = mutableListOf()) {
        var parent: Node? = null
    }

    private fun findRoot(node: T): Node? =
        leafParents[node]?.let { findRoot(it) }

    private fun findRoot(node: Node, pathWeight: Int = 0): Node {
        val strictParent = node.parent ?: return node
        val currentWeight = pathWeight + node.leafs.size
        val foundRoot = findRoot(strictParent, currentWeight)

        if (foundRoot != node) {
            val leafs = node.leafs
            node.rang -= currentWeight
            check(node.rang >= 0)
            foundRoot.leafs.addAll(leafs)
            leafs.clear()
            node.parent = foundRoot
        }

        return foundRoot
    }

    private fun addToRoot(leaf: T, root: Node) {
        leafParents[leaf] = root
        root.rang++
        root.leafs.add(leaf)
    }

    private fun mergeRoots(root1: Node, root2: Node): Node {
        if (root1 == root2) return root1
        require(root1.parent == null && root2.parent == null) { "Merge is possible only for root nodes" }
        if (root2.parent == root1) return root1
        if (root1.parent == root2) return root2

        val rootToMove: Node
        val newParentRoot: Node
        if (root1.rang > root2.rang) {
            rootToMove = root2
            newParentRoot = root1
        } else {
            rootToMove = root1
            newParentRoot = root2
        }

        rootToMove.parent = newParentRoot

        val leafs = rootToMove.leafs
        newParentRoot.rang += leafs.size
        rootToMove.rang -= leafs.size
        check(rootToMove.rang >= 0)
        newParentRoot.leafs.addAll(leafs)
        leafs.clear()

        return newParentRoot
    }

    fun addUnion(elements: List<T>) {
        var root: Node? = null
        dirty = true
        for (leaf in elements) {
            val strictRoot = leafParents[leaf]
            if (strictRoot == null) {
                root = root?.let(::findRoot) ?: Node()
                addToRoot(leaf, root)
            } else {
                val leafRoot = findRoot(strictRoot)
                if (root != null) {
                    root = mergeRoots(root, leafRoot)
                }
            }
        }
    }

    fun compress() {
        leafParents.keys.forEach(::findRoot)
        dirty = false
    }

    fun hasElement(element: T): Boolean =
        leafParents.containsKey(element)

    fun getUnion(element: T): List<T> {
        val root = findRoot(element)
        require(root != null) { "Element not contains in any union" }
        require(!dirty) { "Call compress before getting union" }
        check(root.rang == root.leafs.size) { "Invalid tree state after compress" }
        return root.leafs
    }
}