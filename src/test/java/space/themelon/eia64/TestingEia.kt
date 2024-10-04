package space.themelon.eia64

import space.themelon.eia64.runtime.EiaEventDispatcher
import space.themelon.eia64.runtime.Executor

object TestingEia {
    @JvmStatic
    fun main(args: Array<String>) {
        Executor.STD_LIB = "/home/kumaraswamy/Documents/AppInv/stdlib/"
        val executor = Executor()
        executor.defineJavaObject("Button1", Button())
        executor.loadMainFile("/home/kumaraswamy/Documents/AppInv/examples/event.eia")
        EiaEventDispatcher.dispatchHook(
            "Button1",
            "Click",
            arrayOf("Meow"),
        )
    }
}
