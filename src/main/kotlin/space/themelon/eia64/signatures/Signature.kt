package space.themelon.eia64.signatures

import space.themelon.eia64.signatures.Sign.JAVA
import space.themelon.eia64.syntax.Token

abstract class Signature {
    fun isInt() = this == Sign.INT
    fun isFloat() = this == Sign.FLOAT

    fun isNumeric() = this == Sign.NUM || this == Sign.INT || this == Sign.FLOAT
    fun isNumericOrChar() = isNumeric() || this == Sign.CHAR
    fun isJava() = this == JAVA || this is JavaObjectSign

    fun javaClass(where: Token) = javaClass() ?: where.error("Could not find Java package for sign '${logName()}'")

    fun javaClass(): Class<*> = Class.forName(when (this) {
        Sign.INT -> "java.lang.Integer"
        Sign.FLOAT -> "java.lang.Float"
        Sign.CHAR -> "java.lang.Character"
        Sign.STRING -> "java.lang.String"
        Sign.BOOL -> "java.lang.Boolean"
        JAVA -> "java.lang.Object"
        is JavaObjectSign -> this.clazz.name
        else -> null
    })

    abstract fun logName(): String

    companion object {
        fun signFromJavaClass(clazz: Class<*>) = when (clazz.name) {
            "java.lang.Integer", "int" -> Sign.INT
            "java.lang.Boolean", "boolean" -> Sign.BOOL
            "java.lang.Float", "float" -> Sign.FLOAT
            "java.lang.Character", "char" -> Sign.CHAR
            "java.lang.CharSequence", "java.lang.String" -> Sign.STRING
            "void" -> Sign.NONE
            else -> JavaObjectSign(clazz)
        }
    }
}