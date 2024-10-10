@file:Suppress("unused")

package space.themelon.eia64

import android.util.Log
import com.google.appinventor.components.runtime.*
import gnu.lists.LList
import gnu.mapping.*
import kawa.standard.Scheme
import space.themelon.eia64.expressions.Struct
import space.themelon.eia64.runtime.Conversions.eiaToJava
import space.themelon.eia64.runtime.Executor
import space.themelon.eia64.runtime.Nothing
import space.themelon.eia64.syntax.Token
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.zip.ZipInputStream

object AppInventorInterop {

    private val form = Form.getActiveForm()
    private val environment = form.javaClass.getField("form\$Mnenvironment").get(form) as Environment

    private val stdout = ByteArrayOutputStream()
    private var executor: Executor? = null

    fun init() {
        val executor = Executor().also { this.executor = it }

        executor.standardOutput = PrintStream(stdout)

        val components = if (form.isRepl) mapComponentsRepl() else mapComponents()
        components.forEach {
            Log.d("Eia", "Defining ${it.key} = ${it.value}")
            executor.defineJavaObject(it.key, it.value)
        }
        executor.defineJavaObject("form", form)
    }

    private fun mapComponents(): Map<String, Component> {
        val components = HashMap<String, Component>()
        form.javaClass.fields.forEach {
            if (it.type == Component::class.java) {
                components[it.name] = it.get(form) as Component
            }
        }
        return components
    }

    private fun mapComponentsRepl(): Map<String, Component> {
        val components = HashMap<String, Component>()
        // lookup all the components in form environment
        environment.enumerateAllLocations().forEach { location ->
            location.value.let {
                if (it is Component) {
                    components[location.keySymbol.name] = it
                }
            }
        }
        return components
    }

    // internal use only
    fun registerComponent(
        name: String,
        component: Component,
    ) {
        environment.put(SimpleSymbol(name), component)
    }

    // internal use only
    fun proxyEvent(
        component: String,
        event: String,
        callback: (Array<Any?>) -> Unit
    ) {
        val symbol = SimpleSymbol.valueOf("$component\$$event")
        environment.put(symbol, object: ProcedureN() {
            override fun applyN(eventArgs: Array<Any?>): Any? {
                callback(eventArgs)
                return Values.empty
            }
        })
        EventDispatcher.registerEventForDelegation(form, component, event)
    }

    // internal use only
    fun callProcedure(
        where: Token,
        name: String,
        args: Array<Any?>
    ): Any? {
        var procedure: ProcedureN? = null
        if (form is ReplForm) {
            procedure = Scheme
                .getInstance()
                .eval("(begin (require <com.google.youngandroid.runtime>)(get-var p$$name))") as ProcedureN
        } else {
            val vars = form.javaClass.getField("global\$Mnvars\$Mnto\$Mncreate")[form] as LList
            for (pair in vars) {
                if (LList.Empty == pair) continue
                val asPair = pair as LList
                if ((asPair[0] as Symbol).name == "p$$name") {
                    procedure = (asPair[1] as ProcedureN).apply0() as ProcedureN
                    break
                }
            }
        }
        if (procedure == null) where.error<String>("Could not find procedure '$name'")
        return procedure!!.applyN(args)
    }

    fun execute(source: String): Array<Any> {
        val bytes = stdout.toByteArray()
        val parsed = executor?.parse(source)
        val evaluated = parsed?.let { executor?.evaluate(it)?.eiaToJava() } ?: Nothing.INSTANCE
        return arrayOf(evaluated, bytes)
    }

    fun render(
        parent: AndroidViewComponent,
        source: String,
    ) {
        val parsed = executor?.parse(source)
            ?: throw RuntimeException("Cannot render from a non struct")
        parsed.expressions.forEach {
            if (it !is Struct) {
                throw RuntimeException("Expected a struct expression to render but got " + it.javaClass.simpleName)
            }
            executor?.render(parent, it)
        }
    }
}