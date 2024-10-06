package space.themelon.eia64

import space.themelon.eia64.runtime.Executor
import space.themelon.hello.Cat

object TestingEia {
    @JvmStatic
    fun main(args: Array<String>) {
        Executor.STD_LIB = System.getProperty("user.dir") + "/stdlib/"
        val executor = Executor()
        executor.defineJavaObject("Cat1", Cat("Meow"))
        executor.loadMainSource(javaClass.classLoader.getResource("hi.txt").readText())
        EiaEventDispatcher.dispatchHook("Cat1", "Meow", emptyArray())
    }
}