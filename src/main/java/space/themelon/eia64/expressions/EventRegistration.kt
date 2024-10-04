package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.signatures.Signature
import space.themelon.eia64.syntax.Token

data class EventRegistration(
    val where: Token,
    val jExpression: Expression,
    val eventName: String,
    val args: List<Pair<String, Signature>>,
    val body: Expression,
): Expression(where) {

    override fun <R> accept(v: Visitor<R>) = v.eventRegistration(this)

    override fun sig() = Sign.NONE
}