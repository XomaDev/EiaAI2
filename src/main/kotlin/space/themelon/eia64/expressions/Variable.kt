package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Matching.matches
import space.themelon.eia64.signatures.Signature
import space.themelon.eia64.syntax.Token

data class Variable(
    val where: Token,
    val name: String,
    val expr: Expression,
    val expectSignature: Signature? = null
) : Expression(where) {

    init {
        sig()
    }

    override fun <R> accept(v: Visitor<R>) = v.variable(this)

    override fun sig(): Signature {
        val exprSig = expr.sig()
        if (expectSignature == null) return exprSig
        if (!matches(expect = expectSignature, got = exprSig)) {
            where.error<String>("Variable '$name' expected signature $expectSignature but got $exprSig")
            throw RuntimeException()
        }
        return expectSignature
    }
}