package space.themelon.eia64

import space.themelon.eia64.runtime.Executor
import java.io.*
import java.util.zip.ZipInputStream

class Initialize(
    stdlib: InputStream,
    destFolder: File
) {

    private val stdout = ByteArrayOutputStream()
    private val executor: Executor

    init {
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