package space.themelon.eia64.runtime

import com.google.appinventor.components.runtime.AndroidViewComponent
import space.themelon.eia64.analysis.Parser
import space.themelon.eia64.expressions.ExpressionList
import space.themelon.eia64.expressions.Struct
import space.themelon.eia64.primitives.EJava
import space.themelon.eia64.syntax.Lexer
import java.io.File
import kotlin.system.exitProcess

class Environment {

    companion object {
        var DEBUG = true
        // This unit could be overridden to replace default exitProcess() behaviour
        // When you are demonstrating Eia for e.g., in a server, you shouldn't to allow a random
        // dude to shut down your whole server by doing exit(n) in Eia
        var EIA_SHUTDOWN: (Int) -> Unit = { exitCode -> exitProcess(exitCode) }
    }

    var standardOutput = System.out

    private val evaluator = Evaluator("Main", this)
    private val parser = Parser(this)

    val injections = HashMap<String, EJava>()
    val classInjections = HashMap<String, Class<*>>()

    fun defineJavaObject(name: String, obj: Any) {
        injections += name to EJava(obj, name)

        classInjections += name to obj.javaClass
        classInjections += obj.javaClass.simpleName to obj.javaClass
    }

    fun parse(source: String) = parser.parse(Lexer(source).tokens)
    fun parse(file: File) = parse(file.readText())

    fun evaluate(expressions: ExpressionList) = evaluator.eval(expressions)
    fun render(parent: AndroidViewComponent, struct: Struct) = evaluator.render(parent, struct)

    fun clearMemory() {
        parser.reset()
        evaluator.clearMemory()
    }
}