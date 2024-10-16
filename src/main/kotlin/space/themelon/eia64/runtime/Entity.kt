package space.themelon.eia64.runtime

import space.themelon.eia64.Expression
import space.themelon.eia64.primitives.*
import space.themelon.eia64.signatures.ClassSign
import space.themelon.eia64.signatures.Matching.matches
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.signatures.Signature

open class Entity(
    private val name: String,
    private val mutable: Boolean,
    var value: Any,
    val signature: Signature,
    val interruption: FlowInterrupt = FlowInterrupt.NONE,
) {

    open fun update(another: Any) {
        if (!mutable) throw RuntimeException("Entity $name is immutable")
        if (signature == Sign.ANY) value = another
        else {
            val otherSignature = getSignature(another)
            if (!matches(signature, otherSignature)) throw RuntimeException("Entity $name cannot change type $signature to $otherSignature")
            value = unbox(another)
        }
    }

    companion object {
        fun unbox(value: Any): Any {
            return if (value is Entity) {
                // break that return boxing
                if (value.interruption != FlowInterrupt.NONE) unbox(value.value)
                else value.value
            } else value
        }

        fun getSignature(value: Any): Signature = when (value) {
            is Entity -> {
                // repeatedly break that repeat unboxing
                //  to retrieve the underlying value
                if (value.interruption != FlowInterrupt.NONE) getSignature(value.value)
                else value.signature
            }
            is ENil -> Sign.NIL
            is EInt -> Sign.INT
            is EFloat -> Sign.FLOAT
            is EDouble -> Sign.DOUBLE
            is EString -> Sign.STRING
            is EBool -> Sign.BOOL
            is EChar -> Sign.CHAR
            is EType -> Sign.TYPE
            is EJava -> ClassSign(value.get().javaClass)
            is Expression -> Sign.UNIT
            else -> Sign.JAVA
        }
    }
}