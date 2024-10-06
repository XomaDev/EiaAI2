package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Signature
import space.themelon.eia64.syntax.Token

data class JavaName(
    val where: Token,
    val name: String,
    val static: Boolean,
    val signature: Signature
): Expression(where) {
    override fun <R> accept(v: Visitor<R>) = v.javaName(this)

    override fun sig() = signature
}