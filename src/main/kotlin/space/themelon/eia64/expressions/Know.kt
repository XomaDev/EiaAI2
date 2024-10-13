package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.analysis.ScopeManager
import space.themelon.eia64.mirror.Mirror
import space.themelon.eia64.runtime.Environment
import space.themelon.eia64.signatures.Sign

class Know(
  private val packageName: String,
  shortName: String?
): Expression() {

  private val simpleName = shortName ?: packageName.substring(packageName.lastIndexOf('.') + 1)

  private var clazz: Class<*>? = null

  override fun <R> accept(v: Visitor<R>) =
    v.know(simpleName, clazz ?: Mirror.lookupClass(packageName).also { clazz = it })

  override fun sig(env: Environment, scope: ScopeManager) = Sign.NONE
}