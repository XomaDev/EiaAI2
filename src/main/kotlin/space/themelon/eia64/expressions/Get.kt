package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.syntax.Token

class Get(
    val where: Token,
    val name: Expression,
): Expression(where) {

    override fun <R> accept(v: Visitor<R>) = v.get(this)
    override fun sig() = Sign.JAVA
}