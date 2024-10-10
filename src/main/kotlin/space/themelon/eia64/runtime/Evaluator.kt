package space.themelon.eia64.runtime

import com.google.appinventor.components.runtime.AndroidViewComponent
import com.google.appinventor.components.runtime.Form
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import space.themelon.eia64.AppInventorInterop
import space.themelon.eia64.Expression
import space.themelon.eia64.expressions.*
import space.themelon.eia64.expressions.FunctionExpr
import space.themelon.eia64.primitives.*
import space.themelon.eia64.runtime.Conversions.eiaToJava
import space.themelon.eia64.runtime.Conversions.javaToEia
import space.themelon.eia64.runtime.Entity.Companion.getSignature
import space.themelon.eia64.runtime.Entity.Companion.unbox
import space.themelon.eia64.signatures.*
import space.themelon.eia64.signatures.Matching.matches
import space.themelon.eia64.syntax.Type.*
import java.lang.reflect.Modifier
import java.util.HashMap
import kotlin.collections.ArrayList
import kotlin.math.pow
import kotlin.random.Random

class Evaluator(
    val className: String,
    private val executor: Executor
) : Expression.Visitor<Any> {

    private val startupTime = System.currentTimeMillis()

    private var evaluator: Expression.Visitor<Any> = this

    fun shutdown() {
        // Reroute all the traffic to Void, which would raise ShutdownException.
        // We use this strategy to cause an efficient shutdown than checking fields each time
        evaluator = VoidEvaluator()
    }

    fun eval(expr: Expression) = expr.accept(evaluator)

    fun render(
        parent: AndroidViewComponent,
        struct: Struct
    ) = makeViewComponent(parent, struct)

    private fun unboxEval(expr: Expression) = unbox(eval(expr))

    private fun booleanExpr(expr: Expression) = unboxEval(expr) as EBool

    private fun intExpr(expr: Expression) = when (val result = unboxEval(expr)) {
        is EChar -> EInt(result.get().code)
        else -> result as EInt
    }

    // Plan future: My opinion would be that this should NOT happen, these types of
    // Runtime Checks Hinder performance. There should be a common wrapper to all
    //  the numeric applicable types. Runtime checking should be avoided
    private fun numericExpr(expr: Expression): Numeric = when (val result = unboxEval(expr)) {
        is EChar -> EInt(result.get().code)
        is EInt -> result
        else -> result as EFloat
    }

    // Supply tracer to memory, so that it calls enterScope() and leaveScope()
    // on tracer on behalf of us
    private val memory = Memory()

    fun clearMemory() {
        memory.clearMemory()
    }

    override fun noneExpression() = Nothing.INSTANCE
    override fun nilLiteral(nil: NilLiteral) = ENil()
    override fun intLiteral(literal: IntLiteral) = EInt(literal.value)
    override fun floatLiteral(literal: FloatLiteral) = EFloat(literal.value)
    override fun doubleLiteral(literal: DoubleLiteral) = EDouble(literal.value)

    override fun boolLiteral(literal: BoolLiteral) = EBool(literal.value)
    override fun stringLiteral(literal: StringLiteral) = EString(literal.value)
    override fun charLiteral(literal: CharLiteral) = EChar(literal.value)
    override fun typeLiteral(literal: TypeLiteral) = EType(literal.signature)

    override fun alpha(alpha: Alpha) = memory.getVar(alpha.index, alpha.value)

    override fun javaName(jName: JavaName) = executor.injectedObjects[jName.name]
        ?: jName.where.error("Couldn't find Java Object '${jName.name}'")

    override fun struct(struct: Struct): EJava {
        return EJava(makeViewComponent(Form.getActiveForm(), struct), "Struct<${struct.name}>")
    }

    private fun makeViewComponent(
        parent: Any,
        struct: Struct
    ): AndroidViewComponent {
        val component = struct.constructor.newInstance(parent) as AndroidViewComponent
        AppInventorInterop.registerComponent(struct.identifier, component)

        struct.props.forEach { it.first.invoke(component, unboxEval(it.second).eiaToJava()) }

        val componentName = struct.identifier
        struct.events.forEach { eventInfo ->
            val dispatchInfo = eventInfo.value
            AppInventorInterop.proxyEvent(
                componentName,
                eventInfo.key,
            ) { args ->
                dispatchEvent(
                    componentName,
                    dispatchInfo.first,
                    args,
                    dispatchInfo.second,
                )
            }
        }
        struct.children.forEach { makeViewComponent(component, it) }
        return component
    }

    override fun makeList(makeList: MakeList): Any {
        val list = java.util.ArrayList<Any?>()
        makeList.elements.forEach { list += unboxEval(it) }
        return EJava(list, "makeList<>")
    }

    override fun makeDict(makeDict: MakeDictionary): Any {
        val dictionary = HashMap<Any?, Any?>()
        makeDict.elements.forEach { dictionary += unboxEval(it.first) to unboxEval(it.second) }
        return EJava(dictionary, "makeDict<>")
    }

    private fun update(index: Int, name: String, value: Any) {
        (memory.getVar(index, name) as Entity).update(value)
    }

    // Do not delete it, could be useful in future
    @Suppress("unused")
    private fun update(aMemory: Memory, index: Int, name: String, value: Any) {
        (aMemory.getVar(index, name) as Entity).update(value)
    }

    override fun variable(variable: Variable): Any {
        val name = variable.name
        val value = unboxEval(variable.expr)
        memory.declareVar(name, Entity(name, true, value, variable.sig()))
        return value
    }

    override fun unaryOperation(expr: UnaryOperation): Any = when (val type = expr.operator) {
        EXCLAMATION -> EBool(!(booleanExpr(expr.expr).get()))
        NEGATE -> {
            // first, we need to check the type to ensure we negate Float
            // and Int separately and properly
            val value = numericExpr(expr.expr).get()
            if (expr.sig().isFloat()) EFloat(value.toFloat() * -1)
            else EInt(value.toInt() * -1)
        }

        INCREMENT, DECREMENT -> {
            val numeric = numericExpr(expr.expr)
            val value = if (expr.towardsLeft) {
                if (type == INCREMENT) numeric.incrementAndGet()
                else numeric.decrementAndGet()
            } else {
                if (type == INCREMENT) numeric.getAndIncrement()
                else numeric.getAndDecrement()
            }
            if (value is Int) EInt(value)
            else EFloat(value as Float)
        }

        else -> throw RuntimeException("Unknown unary operator $type")
    }

    private fun valueEquals(left: Any, right: Any) = when (left) {
        is Numeric,
        is EString,
        is EChar,
        is EBool,
        is EJava,
        is ENil,
        is EType -> left == right

        else -> false
    }

    override fun binaryOperation(expr: BinaryOperation) = when (val type = expr.operator) {
        PLUS -> {
            val left = unboxEval(expr.left)
            val right = unboxEval(expr.right)

            if (left is Numeric && right is Numeric) left + right
            else EString(left.toString() + right.toString())
        }

        NEGATE -> numericExpr(expr.left) - numericExpr(expr.right)
        TIMES -> numericExpr(expr.left) * numericExpr(expr.right)
        SLASH -> numericExpr(expr.left) / numericExpr(expr.right)
        REMAINDER -> numericExpr(expr.left) % numericExpr(expr.right)
        EQUALS, NOT_EQUALS -> {
            val left = unboxEval(expr.left)
            val right = unboxEval(expr.right)
            EBool(if (type == EQUALS) valueEquals(left, right) else !valueEquals(left, right))
        }

        LOGICAL_AND -> EBool(booleanExpr(expr.left).get() && (booleanExpr(expr.right).get()))
        LOGICAL_OR -> EBool(booleanExpr(expr.left).get() || booleanExpr(expr.right).get())
        RIGHT_DIAMOND -> EBool(numericExpr(expr.left) > numericExpr(expr.right))
        LEFT_DIAMOND -> EBool(numericExpr(expr.left) < numericExpr(expr.right))
        GREATER_THAN_EQUALS -> EBool(intExpr(expr.left) >= intExpr(expr.right))
        LESSER_THAN_EQUALS -> EBool(intExpr(expr.left) <= intExpr(expr.right))
        ASSIGNMENT -> {
            val toUpdate = expr.left
            val value = unboxEval(expr.right)
            when (toUpdate) {
                is Alpha -> update(toUpdate.index, toUpdate.value, value)
                is JavaFieldAccess -> updateJavaField(toUpdate, value)
                else -> throw RuntimeException("Unknown left operand for [= Assignment]: $toUpdate")
            }
            value
        }

        POWER -> {
            val left = numericExpr(expr.left)
            val right = numericExpr(expr.right)
            EString(left.get().toDouble().pow(right.get().toDouble()).toString())
        }

        BITWISE_AND -> numericExpr(expr.left).and(numericExpr(expr.right))
        BITWISE_OR -> numericExpr(expr.left).or(numericExpr(expr.right))
        else -> throw RuntimeException("Unknown binary operator $type")
    }

    override fun isStatement(isStatement: IsStatement) =
        EBool(matches(isStatement.signature, getSignature(unboxEval(isStatement.expression))))

    override fun expressions(list: ExpressionList): Any {
        if (list.preserveState)
        // it is being stored somewhere, like in a variable, etc.
        //   that's why we shouldn't evaluate it
            return list
        var result: Any? = null
        for (expression in list.expressions) {
            result = eval(expression)
            if (result is Entity) {
                // flow interruption is just forwarded

                // TODO:
                //  We need to verify that these things work
                //when (result.type) {
                //RETURN, BREAK, CONTINUE, USE -> return result
                //else -> { }
                //}
                when (result.interruption) {
                    FlowInterrupt.RETURN,
                    FlowInterrupt.BREAK,
                    FlowInterrupt.CONTINUE,
                    FlowInterrupt.USE -> return result

                    else -> {}
                }
            }
        }
        return result!!
    }

    override fun expressionBind(bind: ExpressionBind): Any {
        bind.expressions.forEach { unboxEval(it) }
        return Nothing.INSTANCE
    }

    override fun newJava(newInstance: NewInstance): Any {
        val evaldArgs = newInstance.arguments.map { unboxEval(it).eiaToJava() }.toTypedArray()
        return EJava(newInstance.constructor.newInstance(*evaldArgs), "INSTANCE(${newInstance.packageName})")
    }

    // try to call a string() method located in local class if available
    @Override
    override fun toString(): String {
        val result = dynamicFnCall(
            "string",
            emptyArray(),
            true,
            "Class<$className>"
        )
        if (result is String) return result
        if (result is EString) return result.get()
        throw RuntimeException("string() returned a non string $result")
    }

    override fun cast(cast: Cast): Any {
        val result = unboxEval(cast.expr)
        val promisedSignature = cast.expectSignature
        val gotSignature = getSignature(result)

        if (promisedSignature is ClassSign) {
            if (gotSignature !is ClassSign) {
                cast.where.error<String>("Cannot cast $result to $promisedSignature")
                throw RuntimeException()
            }
            if (promisedSignature != gotSignature) {
                cast.where.error<String>("Expected class ${promisedSignature.clazz} but got ${gotSignature.clazz}")
                throw RuntimeException()
            }
        } else if (promisedSignature == Sign.JAVA) {
            if (!(gotSignature is ClassSign || gotSignature == Sign.JAVA)) {
                cast.where.error<String>("Cannot cast $result to $promisedSignature")
                throw RuntimeException()
            }
        }
        return result
    }

    override fun nativeCall(call: NativeCall): Any {
        val args = call.args
        val where = call.where
        when (val type = call.call) {
            PRINT -> {
                var printCount = 0
                args.forEach {
                    var printable = unboxEval(it)
                    printable = if (printable is Array<*>) printable.contentDeepToString() else printable.toString()

                    printCount += printable.length
                    executor.standardOutput.print(printable)
                }
                executor.standardOutput.print('\n')
                return Nothing.INSTANCE
            }

            PRINTF -> {
                val string = unboxEval(args[0]).toString()
                executor.standardOutput.print(String.format(string, *args.map { unboxEval(it) }.toTypedArray()))
                return Nothing.INSTANCE
            }

            SLEEP -> {
                Thread.sleep(intExpr(args[0]).get().toLong())
                return Nothing.INSTANCE
            }

            LEN -> {
                return EInt(
                    when (val data = unboxEval(args[0])) {
                        is EString -> data.length
                        is ExpressionList -> data.size
                        is ENil -> 0
                        else -> throw RuntimeException("Unknown measurable data type $data")
                    }
                )
            }

            FORMAT -> {
                val string = unboxEval(args[0])
                if (getSignature(string) != Sign.STRING)
                    throw RuntimeException("format() requires a string argument")
                string as EString
                if (args.size > 1) {
                    val values = arrayOfNulls<Any>(args.size - 1)
                    for (i in 1 until args.size) {
                        val value = unboxEval(args[i])
                        values[i - 1] = if (value is Primitive<*>) value.get() else value
                    }
                    return EString(String.format(string.get(), *values))
                }
                return string
            }

            INT_CAST -> {
                val obj = unboxEval(args[0])

                return when (val objType = getSignature(obj)) {
                    Sign.INT -> obj
                    Sign.CHAR -> EInt((obj as EChar).get().code)
                    Sign.STRING -> EInt(obj.toString().toInt())
                    Sign.FLOAT -> EInt((obj as EFloat).get().toInt())
                    else -> throw RuntimeException("Unknown type for int() cast $objType")
                }
            }

            FLOAT_CAST -> {
                val obj = unboxEval(args[0])

                return when (val objType = getSignature(obj)) {
                    Sign.INT -> (obj as EInt).get().toFloat()
                    Sign.FLOAT -> obj
                    Sign.CHAR -> EFloat((obj as EChar).get().code.toFloat())
                    Sign.STRING -> EFloat(obj.toString().toFloat())
                    else -> throw RuntimeException("Unknown type for int() cast $objType")
                }
            }

            CHAR_CAST -> {
                val obj = unboxEval(args[0])
                return when (val objType = getSignature(obj)) {
                    Sign.CHAR -> objType
                    Sign.INT -> EChar((obj as EInt).get().toChar())
                    else -> throw RuntimeException("Unknown type for char() cast $objType")
                }
            }

            STRING_CAST -> {
                val obj = unboxEval(args[0])
                if (getSignature(obj) == Sign.STRING) return obj
                return EString(obj.toString())
            }

            BOOL_CAST -> {
                val obj = unboxEval(args[0])
                if (getSignature(obj) == Sign.BOOL) return obj
                return EBool(
                    when (obj) {
                        "true" -> true
                        "false" -> false
                        else -> throw RuntimeException("Cannot parse boolean value: $obj")
                    }
                )
            }

            TYPE_OF -> return EType(getSignature(unboxEval(args[0])))

            COPY -> {
                val obj = unboxEval(args[0])
                if (obj !is Primitive<*> || !obj.isCopyable())
                    throw RuntimeException("Cannot apply copy() on object type ${getSignature(obj)} = $obj")
                return obj.copy()!!
            }

            TIME -> return EInt((System.currentTimeMillis() - startupTime).toInt())

            RAND -> {
                val from = intExpr(args[0])
                val to = intExpr(args[1])
                return EInt(Random.nextInt(from.get(), to.get()))
            }

            // don't do a direct exitProcess(n), Eia could be running in a server
            // you don't need the entire server to shut down
            EXIT -> {
                Executor.EIA_SHUTDOWN(intExpr(args[0]).get())
                return EBool(true) // never reached (hopefully?)
            }

            OPEN_SCREEN -> {
                Form.switchForm(unboxEval(args[0]).toString())
                return Nothing.INSTANCE
            }

            CLOSE_SCREEN -> {
                Form.finishActivity()
                return Nothing.INSTANCE
            }

            CLOSE_APP -> {
                Form.finishApplication()
                return Nothing.INSTANCE
            }

            START_VALUE -> return Form.getStartText().javaToEia()
            
            GET -> {
                val name = unboxEval(args[0]).toString()
                return executor.injectedObjects[name] ?: where.error("Couldn't find Java Object '$name'")
            }

            SEARCH -> {
                val regex = Regex(unboxEval(args[0]).toString())
                val expectedName = unboxEval(args[1]).toString()
                val components = ArrayList<Any>()
                executor.injectedObjects.entries.forEach { entry ->
                    if (entry.key.javaClass.simpleName == expectedName && entry.key.matches(regex)) {
                        components += entry.value
                    }
                }
                return components
            }

            PROCEDURE -> {
                if (args.isEmpty()) where.error<String>("Expected minimum of 1 argument for procedure()")
                val name = unboxEval(args[0]).toString()
                val invokeArgs = args.subList(1, args.size).map { unboxEval(it).eiaToJava()}
                return AppInventorInterop.callProcedure(where, name, invokeArgs.toTypedArray()).javaToEia()
            }

            else -> return where.error<String>("Unknown native call operation: '$type'")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun yailConversion(yailConversion: YailConversion): Any {
        val value = unboxEval(yailConversion.expression)
        // expression signatures are already ensured at parse-time
        return if (yailConversion.clazz == YailList::class.java) {
            EJava(YailList.makeList(value as ArrayList<Any?>), "YailList<>")
        } else {
            EJava(YailDictionary.makeDictionary(value as Map<Any?, Any?>), "YailDictionary<>")
        }
    }

    override fun scope(scope: Scope): Any {
        if (scope.imaginary) return eval(scope.expr)
        memory.enterScope()
        val result = eval(scope.expr)
        memory.leaveScope()
        return result
    }

    override fun eventRegistration(registration: EventRegistration): Any {
        val jObj = (unboxEval(registration.jExpression) as EJava)
        AppInventorInterop.proxyEvent(
            jObj.name,
            registration.eventName,
        ) { args ->
            dispatchEvent(
                jObj.name,
                registration.args,
                args,
                registration.body,
            )
        }
        return Nothing.INSTANCE
    }

    private fun dispatchEvent(
        eventName: String,
        requiredArgsSignature: List<Pair<String, Signature>>,
        providedArgs: Array<Any?>,
        body: Expression
    ) {
        if (requiredArgsSignature.size != providedArgs.size) {
            throw RuntimeException("Expected ${requiredArgsSignature.size} for event $eventName but got ${providedArgs.size}")
        }
        memory.enterScope()
        // translate args to eia
        for (i in providedArgs.indices) {
            val argInfo = requiredArgsSignature[i]
            val name = argInfo.first
            val signature = argInfo.second
            val value = providedArgs[i].javaToEia()
            memory.declareVar(name, Entity(name, true, value, signature))
        }
        unboxEval(body)
        memory.leaveScope()
    }

    private fun updateJavaField(field: JavaFieldAccess, value: Any) {
        // do not evaluate field, it will lead to access
        field.field.set((unboxEval(field.jObject) as EJava).get(), value.eiaToJava())
    }

    override fun javaMethodCall(jCall: JavaMethodCall): Any {
        val args = jCall.args.map { unboxEval(it).eiaToJava() }.toTypedArray()
        val method = jCall.method

        val result = if (Modifier.isStatic(method.modifiers)) method.invoke(null, *args)
        else method.invoke(unboxEval(jCall.jObject).eiaToJava(), *args)
        return result.javaToEia()
    }

    override fun javaFieldAccess(access: JavaFieldAccess) =
        access.field.get((unboxEval(access.jObject) as EJava).get()).javaToEia()

    override fun methodCall(call: MethodCall) = fnInvoke(call.reference.fnExpression!!, evaluateArgs(call.arguments))

    private fun evaluateArgs(args: List<Expression>): Array<Any> {
        val evaluatedArgs = arrayOfNulls<Any>(args.size)
        for ((index, expression) in args.withIndex())
            evaluatedArgs[index] = unboxEval(expression)
        @Suppress("UNCHECKED_CAST")
        evaluatedArgs as Array<Any>
        return evaluatedArgs
    }

    private fun dynamicFnCall(
        name: String,
        args: Array<Any>,
        discardIfNotFound: Boolean,
        defaultValue: Any? = null
    ): Any? {
        val fn = memory.dynamicFnSearch(name)
        if (discardIfNotFound && fn == null) return defaultValue
        if (fn == null) throw RuntimeException("Unable to find function '$name()' in class $className")
        return fnInvoke(fn, args)
    }

    private fun fnInvoke(fn: FunctionExpr, callArgs: Array<Any>): Any {
        // Fully Manual Scopped!
        val fnName = fn.name

        val sigArgsSize = fn.arguments.size
        val callArgsSize = callArgs.size

        if (sigArgsSize != callArgsSize)
            reportWrongArguments(fnName, sigArgsSize, callArgsSize)
        val parameters = fn.arguments.iterator()
        val callExpressions = callArgs.iterator()

        val argValues = ArrayList<Pair<String, Any>>() // used for logging only

        val callValues = ArrayList<Pair<Pair<String, Signature>, Any>>()
        while (parameters.hasNext()) {
            val definedParameter = parameters.next()
            val callValue = callExpressions.next()

            callValues += Pair(definedParameter, callValue)
            argValues += Pair(definedParameter.first, callValue)
        }
        memory.enterScope()
        callValues.forEach {
            val definedParameter = it.first
            val value = it.second
            memory.declareVar(
                definedParameter.first,
                Entity(definedParameter.first, true, value, definedParameter.second)
            )
        }
        val result = unboxEval(fn.body)
        memory.leaveScope()
        // Return the function itself as a unit
        if (fn.isVoid) return fn
        return result
    }

    private fun reportWrongArguments(name: String, expectedArgs: Int, gotArgs: Int, type: String = "Fn") {
        throw RuntimeException("$type [$name()] expected $expectedArgs but got $gotArgs")
    }

    override fun until(until: Until): Any {
        // Auto Scopped
        var numIterations = 0
        while (booleanExpr(until.expression).get()) {
            numIterations++
            val result = eval(until.body)
            if (result is Entity) {
                when (result.interruption) {
                    FlowInterrupt.BREAK -> break
                    FlowInterrupt.CONTINUE -> continue
                    FlowInterrupt.RETURN -> return result
                    FlowInterrupt.USE -> result.value
                    else -> {}
                }
            }
        }
        return EInt(numIterations)
    }

    override fun forEach(forEach: ForEach): Any {
        val iterable = unboxEval(forEach.entity)

        var index = 0
        val size: Int

        val getNext: () -> Any
        when (iterable) {
            is EString -> {
                size = iterable.length
                getNext = { iterable.getAt(index++) }
            }

            else -> throw RuntimeException("Unknown non-iterable element $iterable")
        }

        val named = forEach.name
        val body = forEach.body

        var numIterations = 0
        while (index < size) {
            numIterations++
            // Manual Scopped
            memory.enterScope()
            val element = getNext()
            memory.declareVar(named, Entity(named, false, element, getSignature(element)))
            val result = eval(body)
            memory.leaveScope()
            if (result is Entity) {
                when (result.interruption) {
                    FlowInterrupt.BREAK -> break
                    FlowInterrupt.CONTINUE -> continue
                    FlowInterrupt.RETURN -> return result
                    FlowInterrupt.USE -> result.value
                    else -> {}
                }
            }
        }
        return EInt(numIterations)
    }

    override fun itr(itr: Itr): Any {
        val named = itr.name
        var from = intExpr(itr.from)
        val to = intExpr(itr.to)
        val by = if (itr.by == null) EInt(1) else intExpr(itr.by)

        val reverse = from > to
        if (reverse) by.set(EInt(-by.get()))

        var numIterations = 0
        while (if (reverse) from >= to else from <= to) {
            numIterations++
            // Manual Scopped
            memory.enterScope()
            memory.declareVar(named, Entity(named, true, from, Sign.INT))
            val result = eval(itr.body)
            memory.leaveScope()
            if (result is Entity) {
                when (result.interruption) {
                    FlowInterrupt.BREAK -> break
                    FlowInterrupt.CONTINUE -> {
                        from = from + by
                        continue
                    }

                    FlowInterrupt.RETURN -> return result
                    FlowInterrupt.USE -> return result.value
                    else -> {}
                }
            }
            from = from + by
        }
        return EInt(numIterations)
    }

    override fun forLoop(forLoop: ForLoop): Any {
        memory.enterScope()
        forLoop.initializer?.let { eval(it) }

        val conditional = forLoop.conditional

        var numIterations = 0
        fun evalOperational() = forLoop.operational?.let { eval(it) }

        while (if (conditional == null) true else booleanExpr(conditional).get()) {
            numIterations++
            // Auto Scopped
            val result = eval(forLoop.body)
            // Scope -> Memory -> Array
            if (result is Entity) {
                when (result.interruption) {
                    FlowInterrupt.BREAK -> break
                    FlowInterrupt.CONTINUE -> {
                        evalOperational()
                        continue
                    }

                    FlowInterrupt.RETURN -> {
                        memory.leaveScope()
                        return result
                    }

                    FlowInterrupt.USE -> {
                        memory.leaveScope()
                        return result.value
                    }

                    else -> {}
                }
            }
            evalOperational()
        }
        memory.leaveScope()
        return EInt(numIterations)
    }

    override fun interruption(interruption: Interruption) = when (val type = interruption.operator) {
        // wrap it as a normal entity, this will be naturally unboxed when called unbox()
        RETURN -> {
            // could be of a void type, so it could be null
            val expr = if (interruption.expr == null) 0 else unboxEval(interruption.expr)
            Entity(
                "FlowReturn",
                false,
                expr,
                Sign.NONE,
                FlowInterrupt.RETURN
            )
        }

        USE -> Entity(
            "FlowUse",
            false,
            unboxEval(interruption.expr!!),
            Sign.NONE,
            FlowInterrupt.USE
        )

        BREAK -> Entity(
            "FlowBreak",
            false,
            0,
            Sign.NONE,
            FlowInterrupt.BREAK
        )

        CONTINUE -> Entity(
            "FlowContinue",
            false,
            0,
            Sign.NONE,
            FlowInterrupt.CONTINUE
        )

        else -> throw RuntimeException("Unknown interruption type $type")
    }

    override fun whenExpr(whenExpr: When): Any {
        // Fully Auto Scopped
        val matchExpr = unboxEval(whenExpr.expr)
        for (match in whenExpr.matches)
            if (valueEquals(matchExpr, unboxEval(match.first)))
                return unboxEval(match.second)
        return unboxEval(whenExpr.defaultBranch)
    }

    override fun ifFunction(ifExpr: IfStatement): Any {
        val conditionSuccess = booleanExpr(ifExpr.condition).get()
        // Here it would be best if we could add a fallback NONE value that
        // would prevent us from doing a lot of if checks at runtime
        return eval(if (conditionSuccess) ifExpr.thenBody else ifExpr.elseBody)
    }

    override fun function(function: FunctionExpr): Any {
        memory.declareFn(function.name, function)
        return EBool(true)
    }
}
