package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.syntax.Token

data class BoolLiteral(
    val where: Token,
    val value: Boolean
) : Expression() {

    override fun <R> accept(v: Visitor<R>) = v.boolLiteral(this)

    override fun sig(env: Environment, scope: ScopeManager) = Sign.BOOL
}