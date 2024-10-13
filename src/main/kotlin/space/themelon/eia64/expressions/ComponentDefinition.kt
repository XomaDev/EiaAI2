package space.themelon.eia64.expressions

import com.google.appinventor.components.runtime.AndroidViewComponent
import com.google.appinventor.components.runtime.ComponentContainer
import space.themelon.eia64.Expression
import space.themelon.eia64.analysis.ScopeManager
import space.themelon.eia64.runtime.Environment
import space.themelon.eia64.signatures.ClassSign
import space.themelon.eia64.structs.Event
import space.themelon.eia64.structs.Property
import java.lang.reflect.Constructor
import java.lang.reflect.Method

data class ComponentDefinition(
  private val componentName: String,
  val parent: Expression?,
  private val identifier: String?,
  private val properties: List<Property>,
  val events: Map<String, Event>,
  val children: List<ComponentDefinition>,
) : Expression() {

  companion object {
    // just so that we can assign random Ids to components
    var COMPONENTS_CREATED = 0
  }

  var componentId = identifier ?: "XECOMP$${COMPONENTS_CREATED++}"

  var clazz: Class<*>? = null
  var constructor: Constructor<*>? = null

  var methodsProps: List<Pair<Method, Expression>>? = null

  fun lookup() {
    if (clazz != null) return

    val clazz = try {
      Class.forName(Environment.DEFAULT_PACKAGE_BASE + componentName)
    } catch (e: ClassNotFoundException) {
      throw RuntimeException("Cannot find component $componentName")
    }
    this.clazz = clazz
    constructor = clazz?.getConstructor(ComponentContainer::class.java).also { constructor = it }

    methodsProps = properties.map { prop ->
      val method = clazz.methods.find { method ->
        method.parameterCount == 1 && method.name == prop.name
      } ?: prop.token.error("Could not find property '${prop.name}' in $componentName'")
      method to prop.value
    }
  }
  override fun <R> accept(v: Visitor<R>) = v.struct(this)

  override fun sig(env: Environment, scope: ScopeManager) = ClassSign(AndroidViewComponent::class.java)
}