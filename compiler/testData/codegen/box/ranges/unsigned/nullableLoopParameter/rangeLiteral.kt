// IGNORE_BACKEND: JVM
// See KT-38833: Runtime exception is "java.lang.ClassCastException: java.lang.Integer cannot be cast to kotlin.UInt"
// IGNORE_LIGHT_ANALYSIS
// WITH_STDLIB

//abstract class CLASSS {
//    abstract fun foo()
//}
//
//class DERIVED : CLASSS() {
//    override fun foo() = Unit
//}

interface INTERFACE {
    fun foo(i: Int, s: String)
}
interface INTERFACE2 {
    fun boo(i: Int, s: String)
}

class CLASS : INTERFACE, INTERFACE2 {
    public override fun foo(i: Int, s: String) = Unit
    public override fun boo(i: Int, s: String) = Unit
}

fun frrrrr(c: INTERFACE) {
    c.foo(2, "123")
}

fun box(): String {
    frrrrr(CLASS())
    return "OK"
}