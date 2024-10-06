package space.themelon.eia64.expressions

import com.google.appinventor.components.runtime.AndroidViewComponent
import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.JavaObjectSign
import java.lang.reflect.Constructor
import java.lang.reflect.Method

data class Struct(
    val name: String,
    val constructor: Constructor<*>,
    val props: List<Pair<Method, Expression>>,
    val children: List<Struct>,
): Expression() {
    override fun <R> accept(v: Visitor<R>) = v.struct(this)

    override fun sig() = JavaObjectSign(AndroidViewComponent::class.java)
}