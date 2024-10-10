package space.themelon.eia64.runtime

import space.themelon.eia64.primitives.*

object Conversions {
    fun Any.eiaToJava(): Any? {
        if (this !is Primitive<*>)
            throw RuntimeException("Cannot convert to Java: $this")
        if (this is ENil) return null
        return this.get()
    }

    fun Any?.javaToEia(): Primitive<*> {
        return when (this) {
            is Int -> EInt(this)
            is Float -> EFloat(this)
            is String -> EString(this)
            is Boolean -> EBool(this)
            is Char -> EChar(this)
            null -> ENil()
            else -> throw RuntimeException("Cannot translate to eia: $this")
        }
    }
}