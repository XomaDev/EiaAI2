package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.*
import space.themelon.eia64.syntax.Token

data class Cast(
    val where: Token,
    val expr: Expression,
    val expectSignature: Signature
) : Expression(where) {

    // we actually do require evaluating this node at runtime
    override fun <R> accept(v: Visitor<R>) = v.cast(this)

    override fun sig(): Signature {
        val exprSign = expr.sig()
        // Allow casting from Any to <T>
        if (exprSign == Sign.ANY) return expectSignature
        // TODO: check/add this condition at runtime
        if (expectSignature == Sign.ANY) return Sign.ANY

        if (exprSign == expectSignature) {
            // they already are of the same type
            return expectSignature
        }
        where.error<String>("Cannot cast $expr to $expectSignature")
        throw RuntimeException()
    }
}