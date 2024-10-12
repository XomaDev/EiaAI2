package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Matching.matches
import space.themelon.eia64.signatures.Sign
import space.themelon.eia64.signatures.Signature
import space.themelon.eia64.syntax.Token
import space.themelon.eia64.syntax.Type

data class FunctionInfo(
    val returnSignature: Signature?,
    val argsSize: Int,
    val argSignatures: List<Pair<String, Signature>> = emptyList()
)

data class NativeCall(
    val where: Token,
    val call: Type,
    val args: List<Expression>, // sig checked
) : Expression() {

    companion object {
        private val FunctionSignatures = HashMap<Type, FunctionInfo>().apply {
            put(Type.PRINT, FunctionInfo(Sign.NONE, -1))
            put(Type.PRINTF, FunctionInfo(Sign.NONE, -1))
            put(Type.LEN, FunctionInfo(Sign.INT, 1, listOf("measurable" to Sign.ANY)))
            put(Type.SLEEP, FunctionInfo(Sign.NONE, 1, listOf("millis" to Sign.INT)))
            put(Type.RAND, FunctionInfo(Sign.INT, 2, listOf("from" to Sign.INT, "to" to Sign.INT)))
            put(Type.INT_CAST, FunctionInfo(Sign.INT, 1, listOf("intCastable" to Sign.ANY)))
            put(Type.EXIT, FunctionInfo(Sign.NONE, 1, listOf("exitCode" to Sign.INT)))

            put(Type.FLOAT_CAST, FunctionInfo(Sign.FLOAT, 1, listOf("floatCastable" to Sign.ANY)))
            put(Type.CHAR_CAST, FunctionInfo(Sign.CHAR, 1, listOf("charCastable" to Sign.ANY)))
            put(Type.BOOL_CAST, FunctionInfo(Sign.BOOL, 1, listOf("boolCastable" to Sign.ANY)))
            put(Type.STRING_CAST, FunctionInfo(Sign.STRING, 1, listOf("stringCastable" to Sign.ANY)))

            put(Type.TIME, FunctionInfo(Sign.INT, 0))
            put(Type.FORMAT, FunctionInfo(Sign.STRING, -1))
            put(Type.TYPE_OF, FunctionInfo(Sign.TYPE, 1, listOf("any" to Sign.ANY)))

            put(Type.COPY, FunctionInfo(null, 1, listOf("any" to Sign.ANY)))

            // App Inventor
            put(Type.OPEN_SCREEN, FunctionInfo(Sign.NONE, 1, listOf("screenName" to Sign.INT)))
            put(Type.CLOSE_SCREEN, FunctionInfo(Sign.NONE, 0))
            put(Type.CLOSE_APP, FunctionInfo(Sign.NONE, 0))
            put(Type.START_VALUE, FunctionInfo(Sign.STRING, 0))

            put(Type.GET, FunctionInfo(Sign.JAVA, 1, listOf("name" to Sign.STRING)))
            put(Type.SEARCH, FunctionInfo(Sign.LIST, 2, listOf("match" to Sign.STRING, "type" to Sign.STRING)))

            put(Type.PROCEDURE, FunctionInfo(Sign.ANY, -1))
        }
    }

    override fun <R> accept(v: Visitor<R>) = v.nativeCall(this)

    override fun sig(): Signature {
        args.forEach { it.sig() } // functions like println() have indefinite args
        val functionInfo = FunctionSignatures[call] ?: where.error("Could not find native function type $call")
        val expectedArgsSize = functionInfo.argsSize
        val gotArgsSize = args.size
        val callName = call.name

        if (expectedArgsSize != -1 && gotArgsSize != expectedArgsSize) {
            where.error<String>("Function $callName() expected $expectedArgsSize args but got $gotArgsSize")
        }
        val returnSignature = functionInfo.returnSignature ?: return args[0].sig()
        val expectedSignatureIterator = functionInfo.argSignatures.iterator()
        val argumentIterator = args.iterator()

        while (expectedSignatureIterator.hasNext()) {
            val argInfo = expectedSignatureIterator.next()
            val argName = argInfo.first
            val expectedSignature = argInfo.second

            val gotSignature = argumentIterator.next().sig()

            if (!matches(expectedSignature, gotSignature)) {
                where.error<String>("Function $callName() expected arg signature $expectedSignature for $argName but got $gotSignature")
            }
        }

        return returnSignature
    }
}