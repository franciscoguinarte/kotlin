fun Any.test() {
    val x = when (this) {
        is String -> { <!IMPLICIT_CAST_TO_ANY!>{ <!DEBUG_INFO_IMPLICIT_RECEIVER_SMARTCAST!>length<!>  }<!> } // no smartcast on implicit this
        is Int -> { <!IMPLICIT_CAST_TO_ANY!>{ <!DEBUG_INFO_SMARTCAST!>this<!> }<!> } // smartcast on this to Int
        else -> { <!IMPLICIT_CAST_TO_ANY!>1<!> }
    }
    <!UNRESOLVED_REFERENCE!>length<!>
}
