package space.themelon.eia64.signatures

import space.themelon.eia64.signatures.Sign.JAVA

abstract class Signature {
    fun isInt() = this == Sign.INT
    fun isFloat() = this == Sign.FLOAT

    fun isNumeric() = this == Sign.NUM || this == Sign.INT || this == Sign.FLOAT
    fun isNumericOrChar() = isNumeric() || this == Sign.CHAR
    fun isJava() = this == JAVA || this is JavaObjectSign

    abstract fun logName(): String

    companion object {
        fun signFromJavaClass(clazz: Class<*>): Signature {
            return when (clazz.name) {
                "int" -> Sign.INT
                "boolean" -> Sign.BOOL
                "float" -> Sign.FLOAT
                "char" -> Sign.CHAR
                "java.lang.String" -> Sign.STRING
                "void" -> Sign.NONE
                else -> throw RuntimeException("Unknown Java class $clazz")
            }
        }
    }
}