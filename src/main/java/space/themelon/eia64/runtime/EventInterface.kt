package space.themelon.eia64.runtime

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Signature

data class EventInterface(
    private val name: String, // combined name
    private val argsSignature: List<Pair<String, Signature>>,
    private val expression: Expression,
    private val executor: Evaluator,
) {
    fun dispatchEvent(args: Array<Any?>) {
        executor.dispatchEvent(name, argsSignature, args, expression)
    }
}