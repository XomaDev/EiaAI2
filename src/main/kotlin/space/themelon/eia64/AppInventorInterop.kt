@file:Suppress("unused")

package space.themelon.eia64

import android.util.Log
import com.google.appinventor.components.runtime.AndroidViewComponent
import com.google.appinventor.components.runtime.Component
import com.google.appinventor.components.runtime.Form
import gnu.mapping.Environment
import gnu.mapping.ProcedureN
import gnu.mapping.SimpleSymbol
import gnu.mapping.Values
import space.themelon.eia64.expressions.Struct
import space.themelon.eia64.runtime.Executor
import space.themelon.eia64.runtime.Nothing
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

    fun init(extension: Any) {
        val stdlib = form.openAssetForExtension(extension as Component, "stdlib.zip")
        val destFolder = File(form.filesDir, "stdlib/")

        // extract stdlb directory
        destFolder.mkdirs()
        ZipInputStream(stdlib).use {
            var entry = it.nextEntry
            while (entry != null) {
                it.transferTo(FileOutputStream(File(destFolder, entry.name)))
                it.closeEntry()
                entry = it.nextEntry
            }
        }

        Executor.STD_LIB = destFolder.absolutePath

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
    fun proxyEvent(
        component: String,
        event: String,
        callback: (Array<Any?>) -> Boolean
    ) {
        val symbol = SimpleSymbol.valueOf("$component\$$event")
        val oldCallback = environment[symbol] as ProcedureN?
        environment.put(symbol, object: ProcedureN() {
            override fun applyN(eventArgs: Array<Any?>): Any? {
                if (callback(eventArgs))
                    oldCallback?.let { return it.applyN(eventArgs) }
                return Values.empty
            }
        })
    }

    fun execute(source: String): Array<Any> {
        val bytes = stdout.toByteArray()
        val parsed = executor?.parse(source)
        val evaluated = parsed?.let { executor?.evaluate(it) } ?: Nothing.INSTANCE
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