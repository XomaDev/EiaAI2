package space.themelon.eia64.primitives

import space.themelon.eia64.signatures.Matching.matches
import space.themelon.eia64.signatures.Signature

class EType(
    val signature: Signature
): Primitive<EType> {

    override fun set(value: Any) {
        throw UnsupportedOperationException()
    }

    override fun get() = signature

    override fun isCopyable() = true
    // Just return the same instance, this instance would never
    // Change, so it doesn't make a difference
    override fun copy() = this

    override fun equals(other: Any?) = other is EType &&
            (matches(signature, other.signature) || matches(other.signature, signature))
    override fun hashCode() = signature.hashCode()

    override fun toString() = "Type(${signature.logName()})"
}