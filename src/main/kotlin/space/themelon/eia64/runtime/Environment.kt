package space.themelon.eia64.runtime

import com.google.appinventor.components.runtime.AndroidViewComponent
import space.themelon.eia64.analysis.Parser
import space.themelon.eia64.expressions.ExpressionList
import space.themelon.eia64.expressions.ComponentDefinition
import space.themelon.eia64.primitives.EJava
import space.themelon.eia64.syntax.Lexer
import java.io.File

class Environment {

    companion object {
        var DEBUG = true
        const val DEFAULT_PACKAGE_BASE = "com.google.appinventor.components.runtime."
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
    fun render(parent: AndroidViewComponent, componentDefinition: ComponentDefinition) = evaluator.render(parent, componentDefinition)

    fun clearMemory() {
        parser.reset()
        evaluator.clearMemory()
    }
}