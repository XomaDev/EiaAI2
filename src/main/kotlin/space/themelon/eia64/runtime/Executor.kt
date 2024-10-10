package space.themelon.eia64.runtime

import com.google.appinventor.components.runtime.AndroidViewComponent
import space.themelon.eia64.analysis.Parser
import space.themelon.eia64.expressions.ExpressionList
import space.themelon.eia64.expressions.Struct
import space.themelon.eia64.primitives.EJava
import space.themelon.eia64.syntax.Lexer
import java.io.File
import kotlin.system.exitProcess

class Executor {

    companion object {
        var DEBUG = true
        // This unit could be overridden to replace default exitProcess() behaviour
        // When you are demonstrating Eia for e.g., in a server, you shouldn't to allow a random
        // dude to shut down your whole server by doing exit(n) in Eia
        var EIA_SHUTDOWN: (Int) -> Unit = { exitCode -> exitProcess(exitCode) }
    }

    // why do we do this? sometimes while we are developing demonstrable
    // APIs for Eia64, we would want the output to be captured in memory and
    // sent somewhere else
    var standardOutput = System.out
    var standardInput = System.`in`

    private val externalExecutors = HashMap<String, Evaluator>()
    private val mainEvaluator = Evaluator("Main", this)

    private val externalParsers = HashMap<String, Parser>()
    private val mainParser = Parser(this)

    val injectedObjects = HashMap<String, EJava>()
    val knownJavaClasses = HashMap<String, Class<*>>()

    fun defineJavaObject(name: String, obj: Any) {
        injectedObjects += name to EJava(obj, name)

        knownJavaClasses += name to obj.javaClass
        knownJavaClasses += obj.javaClass.simpleName to obj.javaClass
    }

    fun parse(source: String) = mainParser.parse(Lexer(source).tokens)
    fun parse(file: File) = parse(file.readText())

    fun evaluate(expressions: ExpressionList) = mainEvaluator.eval(expressions)
    fun render(parent: AndroidViewComponent, struct: Struct) = mainEvaluator.render(parent, struct)

    // this can be used to enforce restriction on the execution time
    // of the program, while in demonstration environments

    fun shutdownEvaluator() {
        mainEvaluator.shutdown()
    }

    // maybe for internal testing only
    private fun clearMemories() {
        mainEvaluator.clearMemory()
        externalExecutors.values.forEach {
            it.clearMemory()
        }
    }

    // called by parsers, parse the included module
    fun addModule(sourceFile: String, name: String): Boolean {
        if (externalParsers[name] != null) return false
        externalParsers[name] = Parser(this).also { it.parse(getTokens(sourceFile)) }
        return true
    }

    fun getModule(name: String) = externalParsers[name] ?: throw RuntimeException("Could not find module '$name'")

    // loads the included module and executes it
    fun executeModule(name: String): Evaluator {
        val evaluator = newEvaluator(name)
        externalExecutors[name] = evaluator
        return evaluator
    }

    fun newEvaluator(name: String) = Evaluator(name, this).also {
        it.eval((externalParsers[name] ?: throw RuntimeException("Static module '$name') not found")).parsed)
    }

    fun getEvaluator(name: String) = externalExecutors[name]

    private fun getTokens(sourceFile: String) = Lexer(File(sourceFile).readText()).tokens
}