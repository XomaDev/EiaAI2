package space.themelon.eia64

import space.themelon.eia64.runtime.Executor
import space.themelon.hello.Cat

object TestingEia {
    @JvmStatic
    fun main(args: Array<String>) {
        val executor = Executor()
        executor.defineJavaObject("Cat1", Cat("Meow"))
        val parsed = executor.parse(String(javaClass.classLoader.getResourceAsStream("hi.txt").readAllBytes()))
        executor.evaluate(parsed!!)
    }
}