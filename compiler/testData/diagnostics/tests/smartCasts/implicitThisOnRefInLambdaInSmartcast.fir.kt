fun Any.test() {
    val x = when (this) {
        is String -> { { length  } } // no smartcast on implicit this
        is Int -> { { this } } // smartcast on this to Int
        else -> { 1 }
    }
    <!UNRESOLVED_REFERENCE!>length<!>
}
