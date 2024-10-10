package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.syntax.Token

data class MakeDictionary(
    val where: Token,
    val elements: List<Pair<Expression, Expression>>
): Expression(where) {

    override fun <R> accept(v: Visitor<R>) = v.makeDict(this)

    override fun sig() = Sign.DICT
}