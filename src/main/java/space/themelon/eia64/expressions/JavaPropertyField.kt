package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Signature
import space.themelon.eia64.syntax.Token
import java.lang.reflect.Method

data class JavaPropertyField(
    val where: Token,
    val jExpr: Expression,
    val name: String,
    val getMethod: Method?,
    val setMethod: Method?,
    val signature: Signature
): Expression(where) {

    override fun <R> accept(v: Visitor<R>) = v.javaPropertyField(this)

    override fun sig() = signature
}