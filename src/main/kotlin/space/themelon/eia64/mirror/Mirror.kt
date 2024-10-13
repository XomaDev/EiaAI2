package space.themelon.eia64.mirror

object Mirror {

  /**
   * We cannot look up for Class<*> at parse time. It must be done at runtime to ensure
   * class is found. Since parsing and evaluation may take place in different environments
   *
   * It also handles cases where Class names may refer to subclasses.
   * MyClass.SubClass (invalid) → MyClass$SubClass (valid)
   */

  fun lookupClass(className: String): Class<*> {
    var lookupName = className
    while (true) {
      try {
        return Class.forName(lookupName)
      } catch (ignored: ClassNotFoundException) { }
      lookupName.lastIndexOf('.').let {
        if (it == -1) {
          throw RuntimeException("Cannot find class $className")
        }
        lookupName = lookupName.substring(0, it) + '$' + lookupName.substring(it + 1)
      }
    }
  }
}