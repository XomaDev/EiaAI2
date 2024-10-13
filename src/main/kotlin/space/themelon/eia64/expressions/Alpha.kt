package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.analysis.ScopeManager
import space.themelon.eia64.analysis.UniqueVariable
import space.themelon.eia64.runtime.Environment
import space.themelon.eia64.signatures.ClassSign
import space.themelon.eia64.signatures.Signature
import space.themelon.eia64.syntax.Token

data class Alpha(val token: Token) : Expression() {

  private val name = token.data as String

  /**
   * -1 unresolved
   * 0 variable reference
   * 1 injected object reference
   */
  private var referenceType = -1

  private var uniqueVariable: UniqueVariable? = null

  override fun <R> accept(v: Visitor<R>) = when (referenceType) {
    0 -> uniqueVariable!!.let { v.getVar(name, it.index) }
    1 -> v.getInjected(name)
    else -> token.error("sig() was not called")
  }

  override fun sig(env: Environment, scope: ScopeManager): Signature {
    scope.resolveVr(name)?.let {
      uniqueVariable = it
      referenceType = 0
      return it.signature
    }
    env.classInjections[name]?.let {
      referenceType = 1
      return ClassSign(it)
    }
    return token.error("Could not find symbol $name")
  }

}