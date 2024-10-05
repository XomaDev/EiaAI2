package space.themelon.eia64

import space.themelon.eia64.runtime.Executor

object TestingEia {
    @JvmStatic
    fun main(args: Array<String>) {
        Executor.STD_LIB = System.getProperty("user.dir") + "/stdlib/"
        val executor = Executor()
        executor.loadMainSource(javaClass.classLoader.getResource("hi.txt").readText())
    }
}