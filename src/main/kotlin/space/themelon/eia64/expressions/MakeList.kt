package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.syntax.Token

data class MakeList(
    val where: Token,
    val elements: List<Expression>
): Expression() {

    override fun <R> accept(v: Visitor<R>) = v.makeList(this)

    override fun sig(env: Environment, scope: ScopeManager) = Sign.LIST
}