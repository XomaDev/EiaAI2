package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.ClassSign

data class YailConversion(
    val clazz: Class<*>,
    val expression: Expression,
): Expression() {

    override fun <R> accept(v: Visitor<R>) = v.yailConversion(this)

    override fun sig() = ClassSign(clazz)
}