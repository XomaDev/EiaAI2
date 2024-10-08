package space.themelon.eia64.primitives

interface Primitive<T> {
    fun set(value: Any)
    fun get(): Any
    fun isCopyable(): Boolean
    fun copy(): T
}