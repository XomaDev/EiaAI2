package space.themelon.eia64.signatures

data class JavaObjectSign(
    val clazz: Class<*>
): Signature() {
    override fun logName() = "JavaObject($clazz)"
}