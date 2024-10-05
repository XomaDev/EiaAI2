@file:Suppress("unused")

package space.themelon.eia64

import com.google.appinventor.components.runtime.Component
import com.google.appinventor.components.runtime.Form
import gnu.mapping.Environment
import space.themelon.eia64.runtime.Executor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.zip.ZipInputStream

class Initialize(extension: Any) {

    private val form = Form.getActiveForm()

    private val stdout = ByteArrayOutputStream()
    private val executor: Executor

    init {
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

        executor = Executor()
        executor.standardOutput = PrintStream(stdout)

        val components = if (form.isRepl) mapComponentsRepl() else mapComponents()
        components.forEach {
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
        val environment = form.javaClass.getField("form\$Mnenvironment").get(form) as Environment
        environment.enumerateAllLocations().forEach { location ->
            location.value.let {
                if (it is Component) {
                    components[location.keySymbol.name] = it
                }
            }
        }
        return components
    }

    fun execute(source: String): Array<Any> {
        val bytes = stdout.toByteArray()
        stdout.reset()
        return arrayOf(executor.loadMainSource(source), bytes)
    }
}