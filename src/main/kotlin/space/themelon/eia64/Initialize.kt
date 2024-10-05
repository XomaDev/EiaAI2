@file:Suppress("unused")

package space.themelon.eia64

import com.google.appinventor.components.runtime.Component
import com.google.appinventor.components.runtime.Form
import space.themelon.eia64.runtime.Executor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.zip.ZipInputStream

class Initialize(extension: Any) {

    private val stdout = ByteArrayOutputStream()
    private val executor: Executor

    init {
        val form = Form.getActiveForm()
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
    }

    fun execute(source: String): Array<Any> {
        val bytes = stdout.toByteArray()
        stdout.reset()
        return arrayOf(executor.loadMainSource(source), bytes)
    }
}