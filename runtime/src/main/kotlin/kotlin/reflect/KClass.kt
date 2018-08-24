/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlin.reflect

/**
 * Represents a class and provides introspection capabilities.
 * Instances of this class are obtainable by the `::class` syntax.
 * See the [Kotlin language documentation](http://kotlinlang.org/docs/reference/reflection.html#class-references)
 * for more information.
 *
 * @param T the type of the class.
 */
public interface KClass<T : Any> : KDeclarationContainer, KAnnotatedElement, KClassifier {
    /**
     * The simple name of the class as it was declared in the source code,
     * or `null` if the class has no name (if, for example, it is an anonymous object literal).
     */
    public val simpleName: String?

    /**
     * The fully qualified dot-separated name of the class,
     * or `null` if the class is local or it is an anonymous object literal.
     */
    public val qualifiedName: String?

    /**
     * Returns `true` if [value] is an instance of this class on a given platform.
     */
    @SinceKotlin("1.1")
    public fun isInstance(value: Any?): Boolean

    /**
     * Returns `true` if this [KClass] instance represents the same Kotlin class as the class represented by [other].
     * On JVM this means that all of the following conditions are satisfied:
     *
     * 1. [other] has the same (fully qualified) Kotlin class name as this instance.
     * 2. [other]'s backing [Class] object is loaded with the same class loader as the [Class] object of this instance.
     * 3. If the classes represent [Array], then [Class] objects of their element types are equal.
     *
     * For example, on JVM, [KClass] instances for a primitive type (`int`) and the corresponding wrapper type (`java.lang.Integer`)
     * are considered equal, because they have the same fully qualified name "kotlin.Int".
     */
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}
