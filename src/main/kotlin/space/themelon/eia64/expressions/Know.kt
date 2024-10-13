package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign

class Know(
  private val packageName: String,
  shortName: String?
): Expression() {

  private val simpleName = shortName ?: packageName.substring(packageName.lastIndexOf('.') + 1)

  private var clazz: Class<*>? = null

  /**
   * We cannot look up for Class<*> at parse time. It must be done at runtime to ensure
   * class is found. Since parsing and evaluation may take place in different environments
   *
   * It also handles cases where Class names may refer to subclasses.
   * MyClass.SubClass (invalid) → MyClass$SubClass (valid)
   */

  private fun lookupClass(): Class<*> {
    clazz?.let { return it }
    var lookupName = packageName
    while (true) {
      try {
        return Class.forName(lookupName).also { clazz = it }
      } catch (ignored: ClassNotFoundException) { }
      lookupName.lastIndexOf('.').let {
        if (it == -1) {
          throw RuntimeException("Cannot find class $packageName")
        }
        lookupName = lookupName.substring(0, it) + '$' + lookupName.substring(it + 1)
      }
    }
  }

  override fun <R> accept(v: Visitor<R>) = v.know(simpleName, lookupClass())

  override fun sig() = Sign.NONE
}