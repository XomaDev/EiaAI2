package space.themelon.eia64.expressions

import com.google.appinventor.components.runtime.AndroidViewComponent
import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.ClassSign
import space.themelon.eia64.signatures.Signature
import java.lang.reflect.Constructor
import java.lang.reflect.Method

data class Struct(
    val identifier: String, // a custom identifier, such as `Button1`
    val name: String, // name of the component, `Button`
    val constructor: Constructor<*>,
    val props: List<Pair<Method, Expression>>,
    // Map < Event Name > = Pair( Lis<ArgSignature>, ExpressionCallback )
    val events: Map<String, Pair<List<Pair<String, Signature>>, Expression>>,
    val children: List<Struct>,
): Expression() {
    override fun <R> accept(v: Visitor<R>) = v.struct(this)

    override fun sig() = ClassSign(AndroidViewComponent::class.java)
}