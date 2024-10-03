package space.themelon.eia64.signatures

import space.themelon.eia64.Expression
import space.themelon.eia64.syntax.Token

object Matching {

    fun numericOrChar(first: Expression, second: Expression) =
        first.sig().isNumericOrChar() && second.sig().isNumericOrChar()

    fun matches(expect: Signature, got: Signature): Boolean {
        if (expect == Sign.NUM) return got.isNumeric()
        if (expect == Sign.ARRAY && got is ArrayExtension) return true
        if (expect == Sign.JAVA && (got == Sign.JAVA || got is JavaObjectSign)) return true
        if (got == Sign.NIL) return true
        if (expect == Sign.ANY) return got != Sign.NONE
        if (expect is SimpleSignature) return expect == got

        if (expect is ArrayExtension) {
            if (got !is ArrayExtension) return false
            return expect.elementSignature == Sign.ANY
                    || expect.elementSignature == got.elementSignature
        }

        if (expect is ObjectExtension) {
            if (got !is ObjectExtension) return false
            if (expect.extensionClass == Sign.ANY.type
                || expect.extensionClass == Sign.OBJECT.type
            ) return true
            return expect.extensionClass == got.extensionClass
        }
        // verify underlying classes are same
        if (expect is JavaObjectSign && got is JavaObjectSign)
            return expect == got
        return false
    }

    fun verifyNonVoids(expressions: List<Expression>, where: Token, message: String) {
        for (expression in expressions) {
            val signature = expression.sig()
            if (signature == Sign.NONE) {
                where.error<String>(message)
                throw RuntimeException()
            }
        }
    }
}