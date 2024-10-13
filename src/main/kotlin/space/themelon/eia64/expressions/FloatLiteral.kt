package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.syntax.Token

data class FloatLiteral(
    val where: Token,
    val value: Float
) : Expression() {

    override fun <R> accept(v: Visitor<R>) = v.floatLiteral(this)

    override fun sig(env: Environment, scope: ScopeManager) = Sign.FLOAT
}