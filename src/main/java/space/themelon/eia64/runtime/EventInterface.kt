package space.themelon.eia64.runtime

import space.themelon.eia64.Expression
import space.themelon.eia64.runtime.Conversions.javaToEia
import space.themelon.eia64.signatures.Signature

data class EventInterface(
    private val name: String, // combined name
    private val args: List<Pair<String, Signature>>,
    private val expression: Expression,
    private val executor: Evaluator,
) {
    fun dispatchEvent(args: ArrayList<Any>) {
        if (args.size != this.args.size) {
            throw RuntimeException("Args mismatch while event dispatching `$name` expected ${this.args.size} but got ${args.size}")
        }
        for ((index, _) in args.withIndex()) {
            args[index] = args[index].javaToEia()
        }
    }
}