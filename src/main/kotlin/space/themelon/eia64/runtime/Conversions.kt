package space.themelon.eia64.runtime

import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import space.themelon.eia64.primitives.*

object Conversions {

    fun Any.eiaToJava(): Any? {
        if (this !is Primitive<*>)
            throw RuntimeException("Cannot convert to Java: $this")
        if (this is ENil) return null
        if (this is EJava) {
            // we got to do deep translation
            @Suppress("UNCHECKED_CAST")
            return when (val instance = get()) {
                is java.util.ArrayList<*> -> YailList.makeList(instance.map { it.eiaToJava() })
                is java.util.HashMap<*, *> -> YailDictionary.makeDictionary(
                    instance.map { it.key.eiaToJava() to it.value.eiaToJava() } as Map<Any?, Any?>
                )
                else -> instance
            }
        }
        return get()
    }

    fun Any?.javaToEia(): Primitive<*> {
        return when (this) {
            is Int -> EInt(this)
            is Float -> EFloat(this)
            is String -> EString(this)
            is Boolean -> EBool(this)
            is Char -> EChar(this)
            is YailList -> {
                // convert to java.util.ArrayList<Any?>
                val elements = ArrayList<Any?>()
                forEach { elements += it }
                EJava(elements, "FromYailList<>")
            }
            is YailDictionary -> {
                // convert to java.util.HashMap<Any?, Any?>
                val elements = HashMap<Any?, Any?>()
                forEach { t, u -> elements += t to u }
                EJava(elements, "FromYailDict<>")
            }
            null -> ENil()
            else -> EJava(this, "${this::class.java.name}<>")
        }
    }
}