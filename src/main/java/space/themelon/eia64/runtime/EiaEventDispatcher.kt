package space.themelon.eia64.runtime

object EiaEventDispatcher {

    private val hooks = HashMap<String, EventInterface>()

    fun registerEvent(
        component: String,
        event: String,
        eventInterface: EventInterface) {
        hooks["$component.$event"] = eventInterface
    }

    fun dispatchHook(component: String, event: String) {
        hooks["$component.$event"]?.let { it }
    }
}