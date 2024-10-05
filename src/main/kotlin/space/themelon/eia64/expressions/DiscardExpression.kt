package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Signature

class DiscardExpression: Expression() {
    // this expression is meant to be discarded
    override fun <R> accept(v: Visitor<R>): R {
        throw UnsupportedOperationException()
    }

    override fun sig(): Signature {
        throw UnsupportedOperationException()
    }
}