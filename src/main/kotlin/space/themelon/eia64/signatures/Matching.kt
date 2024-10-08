package space.themelon.eia64.signatures

import space.themelon.eia64.Expression

object Matching {

    fun numericOrChar(first: Expression, second: Expression) =
        first.sig().isNumericOrChar() && second.sig().isNumericOrChar()

    fun matches(expect: Signature, got: Signature): Boolean {
        if (expect == Sign.NUM) return got.isNumeric()
        if (expect == Sign.JAVA && (got == Sign.JAVA || got is ClassSign)) return true
        if (got == Sign.NIL) return true
        if (expect == Sign.ANY) return got != Sign.NONE
        if (expect is SimpleSignature) return expect == got

        // verify underlying classes are same
        if (expect is ClassSign && got is ClassSign)
            return expect.clazz.isAssignableFrom(got.clazz)
        return false
    }
}