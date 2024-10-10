package space.themelon.eia64.analysis

import com.google.appinventor.components.runtime.ComponentContainer
import space.themelon.eia64.Expression
import space.themelon.eia64.expressions.*
import space.themelon.eia64.runtime.Executor
import space.themelon.eia64.signatures.*
import space.themelon.eia64.signatures.Matching.matches
import space.themelon.eia64.syntax.Flag
import space.themelon.eia64.syntax.Token
import space.themelon.eia64.syntax.Type
import space.themelon.eia64.syntax.Type.*
import java.lang.reflect.Method

class Parser(
    private val executor: Executor,
) {

    companion object {
        val COMPONENTS_PACKAGE = "com.google.appinventor.components.runtime."
    }

    private val manager = ScopeManager()

    private lateinit var tokens: List<Token>
    private var index = 0
    private var size = 0

    lateinit var parsed: ExpressionList

    fun parse(tokens: List<Token>): ExpressionList? {
        index = 0
        size = tokens.size
        this.tokens = tokens

        val expressions = ArrayList<Expression>()
        parseScopeOutline()
        while (!isEOF()) parseStatement().let { if (it !is DiscardExpression) expressions += it }
        if (expressions.isEmpty()) return null
        if (Executor.DEBUG) expressions.forEach { println(it) }
        parsed = ExpressionList(expressions)
        return parsed
    }


    // make sure to update canParseNext() when we add stuff here!
    private fun parseStatement(): Expression {
        val token = next()
        if (token.flags.isNotEmpty()) {
            when (token.flags[0]) {
                Flag.LOOP -> return loop(token)
                Flag.V_KEYWORD -> return variableDeclaration(false, token)
                Flag.INTERRUPTION -> return interruption(token)
                else -> {}
            }
        }
        return when (token.type) {
            IF -> ifDeclaration(token)
            FUN -> fnDeclaration()
            NEW -> newStatement(token)
            IMPORT -> importStatement()
            else -> {
                back()
                parseExpr(0)
            }
        }
    }

    private fun canParseNext(): Boolean {
        val token = peek()
        if (token.flags.isNotEmpty())
            token.flags[0].let {
                if (it == Flag.LOOP
                    || it == Flag.V_KEYWORD
                    || it == Flag.INTERRUPTION)
                    return true
            }
        return when (token.type) {
            IF,
            FUN,
            IMPORT,
            NEW -> true
            //WHEN -> true
            else -> false
        }
    }

    private fun parseScopeOutline() {
        // We'll be bumping Indexes, so save it to set back later
        val originalIndex = index

        var curlyBracesCount = 0

        fun handleFn(visible: Boolean) {
            // A function, now we parse its signature!
            val reference = functionOutline(visible)
            // Predefine all the outlines!
            manager.defineSemiFn(reference.name, reference)
        }

        while (!isEOF()) {
            val token = next()
            when (val type = token.type) {
                OPEN_CURLY -> curlyBracesCount++
                CLOSE_CURLY -> {
                    if (curlyBracesCount == 0) break
                    else curlyBracesCount--
                }
                FUN -> if (curlyBracesCount == 0) handleFn(false)
                else -> { }
            }
        }

        index = originalIndex
    }

    private fun importStatement(): Expression {
        val pkgNameParts = ArrayList<String>()
        pkgNameParts += readAlpha()
        while (consumeNext(DOT)) {
            pkgNameParts += readAlpha()
        }
        val clazz = Class.forName(pkgNameParts.joinToString("."))
        executor.knownJavaClasses += clazz.simpleName to clazz

        // there's nothing to do at runtime
        return DiscardExpression()
    }

    // this function was repurposed for making java instances
    private fun newStatement(token: Token): Expression {
        val name = readAlpha()
        val clazz = executor.knownJavaClasses[name]
            ?: token.error("Cannot find symbol '$name'")
        expectType(OPEN_CURVE)
        val args = parseArgs()
        expectType(CLOSE_CURVE)

        // now we have to find a suitable constructor,
        // it's not very sophisticated though, but it does the work (for now?)
        val constructor = clazz.constructors.find { it.parameterCount == args.size }
            ?: token.error("Could not find constructor of args size ${args.size}")
        return NewInstance(
            token,
            clazz,
            clazz.name,
            constructor,
            args
        )
    }

    private fun parseNextInBrace(): Expression {
        // we do it this way, just calling parseNext() would work, but it increases code flow
        // which may make it harder to debug the Parser.

        expectType(OPEN_CURVE)
        val expr = parseStatement()
        expectType(CLOSE_CURVE)
        return expr
    }

    private fun loop(where: Token): Expression {
        when (where.type) {
            UNTIL -> {
                val expr = parseNextInBrace()
                // Scope: Automatic
                val body = manager.iterativeScope { autoScopeBody() }
                return Until(where, expr, body)
            }

            FOR -> {
                // we cannot expose initializers outside the for loop
                expectType(OPEN_CURVE)
                return if (isNext(ALPHA)) forEach(where) else forVariableLoop(where)
            }

            EACH -> {
                expectType(OPEN_CURVE)
                val iName = readAlpha()
                expectType(COLON)

                val from = parseStatement()
                expectType(TO)
                val to = parseStatement()

                var by: Expression? = null
                if (isNext(BY)) {
                    skip()
                    by = parseStatement()
                }
                expectType(CLOSE_CURVE)
                manager.enterScope()
                manager.defineVariable(iName, Sign.INT, false)
                // Manual Scopped!
                val body = manager.iterativeScope { unscoppedBodyExpr() }
                manager.leaveScope()
                return Itr(where, iName, from, to, by, body)
            }

            else -> return where.error("Unknown loop type symbol")
        }
    }

    private fun forEach(where: Token): ForEach {
        val iName = readAlpha()
        expectType(IN)
        val entity = parseStatement()
        expectType(CLOSE_CURVE)

        val elementSignature = when (entity.sig()) {
            Sign.ARRAY -> Sign.ANY
            Sign.STRING -> Sign.CHAR

            else -> {
                where.error<String>("Unknown non iterable element for '$iName'")
                throw RuntimeException()
            }
        }

        manager.enterScope()
        manager.defineVariable(iName, elementSignature, false)
        // Manual Scopped!
        val body = manager.iterativeScope { unscoppedBodyExpr() }
        manager.leaveScope()
        return ForEach(where, iName, entity, body)
    }

    private fun forVariableLoop(
        where: Token,
    ): ForLoop {
        manager.enterScope()
        val initializer = if (isNext(SEMI_COLON)) null else parseStatement()
        expectType(SEMI_COLON)
        val conditional = if (isNext(SEMI_COLON)) null else parseStatement()
        expectType(SEMI_COLON)
        val operational = if (isNext(CLOSE_CURVE)) null else parseStatement()
        expectType(CLOSE_CURVE)
        // double layer scope wrapping
        // Scope: Automatic
        val body = manager.iterativeScope { autoBodyExpr() }
        manager.leaveScope()
        return ForLoop(
            where,
            initializer,
            conditional,
            operational,
            body
        )
    }

    private fun interruption(token: Token): Interruption {
        // checks if `continue and `break` statement are allowed
        if ((token.type == CONTINUE || token.type == BREAK) && !manager.isIterativeScope) {
            val type = if (token.type == CONTINUE) "Continue" else "Break"
            token.error<String>("$type statement is not allowed here") // End of Execution
            throw RuntimeException()
        }
        return Interruption(
            token,
            token.type,
            when (token.type) {
                RETURN -> {
                    val expectedSignature = manager.getPromisedSignature
                    if (expectedSignature == Sign.NONE) {
                        null
                    } else {
                        val expr = parseStatement()
                        val gotSignature = expr.sig()
                        if (!matches(expectedSignature, gotSignature)) {
                            token.error<String>("Was expecting return type of $expectedSignature but got $gotSignature")
                            throw RuntimeException()
                        }
                        expr
                    }
                }
                USE -> parseStatement()
                else -> null
            }
        )
    }

    private fun functionOutline(public: Boolean): FunctionReference {
        val where = next()
        val name = readAlpha(where)

        val requiredArgs = readRequiredArgs()

        val isVoid: Boolean
        val returnSignature = if (isNext(COLON)) {
            skip()
            isVoid = false
            readSignature(next())
        } else {
            isVoid = true
            Sign.UNIT
        }

        return FunctionReference(
            where,
            name,
            null,
            requiredArgs,
            requiredArgs.size,
            returnSignature,
            isVoid,
            public,
            index
        )
    }

    private fun readRequiredArgs(): List<Pair<String, Signature>> {
        expectType(OPEN_CURVE)
        val requiredArgs = mutableListOf<Pair<String, Signature>>()
        while (!isEOF() && peek().type != CLOSE_CURVE) {
            val parameterName = readAlpha()
            expectType(COLON)
            val signature = readSignature(next())

            requiredArgs += parameterName to signature
            if (!isNext(COMMA)) break
            skip()
        }
        expectType(CLOSE_CURVE)
        return requiredArgs
    }

    private fun fnDeclaration(): FunctionExpr {
        val reference = manager.readFnOutline()
        index = reference.tokenIndex
        manager.enterScope()
        reference.parameters.forEach { manager.defineVariable(it.first, it.second, false) }

        val body: Expression = if (isNext(ASSIGNMENT)) {
            skip()
            parseStatement()
        } else {
            manager.expectReturn(reference.returnSignature) {
                expressions()
            }
        }
        manager.leaveScope()

        val fnExpr = FunctionExpr(
            reference.where,
            reference.name,
            reference.parameters,
            reference.isVoid,
            reference.returnSignature,
            body
        )
        reference.fnExpression = fnExpr
        return fnExpr
    }



    private fun ifDeclaration(where: Token): IfStatement {
        val logicalExpr = parseNextInBrace()
        val ifBody = autoBodyExpr()

        // All is Auto Scopped!
        // Here we need to know if the Is Statement is terminative or not
        //
        // Case 1 => Terminative (meaning end-of execution if the body is executed)
        //   then => then we treat the rest of the body as an else function

        if (isEOF() || peek().type != ELSE)
            return IfStatement(where, logicalExpr, ifBody, NoneExpression.INSTANCE)
        skip()

        val elseBranch = when (peek().type) {
            IF -> ifDeclaration(next())
            else -> autoBodyExpr()
        }
        return IfStatement(where, logicalExpr, ifBody, elseBranch)
    }

    private fun autoBodyExpr(): Scope {
        // used everywhere where there is no manual scope management is required,
        //  e.g., IfExpr, Until, For
        if (peek().type == OPEN_CURLY) return autoScopeBody()
        manager.enterScope()
        return Scope(parseStatement(), manager.leaveScope())
    }

    private fun autoScopeBody(): Scope {
        manager.enterScope()
        return Scope(expressions(), manager.leaveScope())
    }

    private fun unscoppedBodyExpr(): Expression {
        if (peek().type == OPEN_CURLY) return expressions()
        return parseStatement()
    }

    private fun expressions(): Expression {
        expectType(OPEN_CURLY)
        parseScopeOutline()
        val expressions = ArrayList<Expression>()
        if (peek().type == CLOSE_CURLY)
            return ExpressionList(expressions)
        while (!isEOF() && peek().type != CLOSE_CURLY)
            expressions.add(parseStatement())
        expectType(CLOSE_CURLY)
        // such optimisations may alter expressions behaviour
        // if (expressions.size == 1) return expressions[0]
        return ExpressionList(expressions)
    }

    private fun variableDeclaration(public: Boolean, where: Token): Expression {
        //if (!isNext(EXCLAMATION)) {
            // for now, later when ';' will be swapped with //, we won't need it
            //return readVariableDeclaration(where, public)
        //}
        // '!' mark after let or var represents multi expressions
        //next()
        // note => same modifier applied to all variables
        val expressions = mutableListOf<Expression>()
        do {
            // read minimum one declaration
            expressions += readVariableDeclaration(where, public)
            //println("Iteration: " + expressions.last())
        } while (isNext(COMMA).also { if (it) next() })

        if (expressions.size == 1) return expressions.first()
        return ExpressionBind(expressions)
    }

    private fun readVariableDeclaration(
        where: Token,
        public: Boolean
    ): Expression {
        val name = readAlpha()

        val expr: Expression
        val signature: Signature

        if (!isNext(COLON)) {
            val assignmentExpr = readVariableExpr()
            signature = assignmentExpr.sig()
            expr = Variable(where, name, assignmentExpr)
        } else {
            skip()
            signature = readSignature(next())
            expr = Variable(
                where,
                name,
                readVariableExpr(),
                signature
            )
        }
        manager.defineVariable(name, signature, public)
        return expr
    }

    private fun readSignature(token: Token): Signature {
        if (token.hasFlag(Flag.CLASS)) {
            // then wrap it around Simple Signature
            return when (val classType = token.type) {
                E_NUMBER -> Sign.NUM
                E_NIL -> Sign.NIL
                E_INT -> Sign.INT
                E_FLOAT -> Sign.FLOAT
                E_STRING -> Sign.STRING
                E_CHAR -> Sign.CHAR
                E_BOOL -> Sign.BOOL
                E_ANY -> Sign.ANY
                E_UNIT -> Sign.UNIT
                E_TYPE -> Sign.TYPE
                E_JAVA -> {
                    if (consumeNext(OPEN_CURVE)) {
                        val name = readAlpha()
                        expectType(CLOSE_CURVE)
                        return ClassSign(Class.forName(name))
                    } else {
                        return Sign.JAVA
                    }
                }
                else -> token.error("Unknown class $classType")
            }
        }

        if (token.type != ALPHA) {
            token.error<String>("Expected a class type")
            // end of execution
        }
        token.error<String>("Unknown class ${token.data}")
        throw RuntimeException()
    }

    private fun readVariableExpr(): Expression {
        val nextToken = peek()
        return when (nextToken.type) {
            ASSIGNMENT -> {
                skip()
                parseStatement()
            }

            OPEN_CURLY -> parseStatement()
            else -> nextToken.error("Unexpected variable expression")
        }
    }

    private fun parseExpr(minPrecedence: Int): Expression {
        // this parses a full expressions, until it's done!
        var left = parseElement()
        if (!isEOF() && peek().hasFlag(Flag.POSSIBLE_RIGHT_UNARY)) {
            val where = next()
            left = UnaryOperation(where, where.type, left, false)
        }
        while (!isEOF()) {
            val opToken = peek()
            if (!opToken.hasFlag(Flag.OPERATOR)) return left

            val precedence = operatorPrecedence(opToken.flags[0])
            if (precedence == -1) return left

            if (precedence < minPrecedence) return left

            skip() // operator token
            if (opToken.type == IS) {
                val signature = readSignature(next())
                left = IsStatement(left, signature)
            } else {
                val right =
                    if (opToken.hasFlag(Flag.PRESERVE_ORDER)) parseElement()
                    else parseExpr(precedence)
                left = makeBinaryExpr(
                    opToken,
                    left,
                    right,
                    opToken.type
                )
            }
        }
        return left
    }

    private fun makeBinaryExpr(
        opToken: Token,
        left: Expression,
        right: Expression,
        type: Type
    ): BinaryOperation {
        if (opToken.hasFlag(Flag.SPREAD)) {
            // We gotta translate `x += 2` into `x = x + 2`
            // wen token has `SPREAD` flag
            val newType = when (type) {
                ADDITIVE_ASSIGNMENT -> PLUS
                DEDUCTIVE_ASSIGNMENT -> NEGATE
                MULTIPLICATIVE_ASSIGNMENT -> MULTIPLICATIVE_ASSIGNMENT
                DIVIDIVE_ASSIGNMENT -> SLASH
                REMAINDER_ASSIGNMENT -> REMAINDER
                else -> throw RuntimeException("Unexpected operator $opToken")
            }
            val newOpToken = Token(opToken.lineCount, newType)

            // extreme left => left
            // extreme right => ( left  [opToken] right  )
            val newRight = BinaryOperation(newOpToken, left, right, newType)
            return BinaryOperation(
                newOpToken,
                left,
                newRight,
                ASSIGNMENT
            )
        }
        return BinaryOperation(
            opToken,
            left,
            right,
            type
        )
    }

    private fun parseElement(): Expression {
        var left = parseTerm()
        // checks for calling methods located in different classes and also
        //  for array access parsing
        while (!isEOF()) {
            val nextOp = peek()
            if (nextOp.type != DOT // (left is class) trying to call a method on an object. e.g. person.sayHello()
                && !(nextOp.type == OPEN_CURVE && !isLiteral(left)) // (left points/is a unit)
                && nextOp.type != OPEN_SQUARE // array element access
                && nextOp.type != DOUBLE_COLON // value casting
                && nextOp.type != COLON // event registration
            ) break

            left = when (nextOp.type) {
                // calling shadow func
                OPEN_CURVE -> unitCall(left)
                DOUBLE_COLON -> {
                    skip()
                    Cast(nextOp, left, readSignature(next()))
                }
                COLON -> {
                    skip()
                    eventRegistration(left)
                }
                else -> javaCall(left)
            }
        }
        return left
    }

    private fun isLiteral(expression: Expression) = when (expression) {
        is NilLiteral,
        is IntLiteral,
        is FloatLiteral,
        is DoubleLiteral,
        is StringLiteral,
        is BoolLiteral,
        is CharLiteral -> true

        else -> false
    }

    private fun eventRegistration(jExpr: Expression): Expression {
        // Button1:Click() { .. }
        if (!jExpr.sig().isJava())
            throw RuntimeException("Cannot register events on non Java objects")
        val where = next()
        val eventName = readAlpha(where)
        val requiredArgs = mutableListOf<Pair<String, Signature>>()
        manager.enterScope()
        if (consumeNext(OPEN_CURVE)) {
            while (!isEOF() && peek().type != CLOSE_CURVE) {
                val parameterName = readAlpha()
                expectType(COLON)
                val signature = readSignature(next())

                manager.defineVariable(parameterName, signature, false)
                requiredArgs += parameterName to signature
                if (!isNext(COMMA)) break
                skip()
            }
            expectType(CLOSE_CURVE)
        }
        val body = expressions()
        manager.leaveScope()
        return EventRegistration(where, jExpr, eventName, requiredArgs, body)
    }

    private fun javaCall(left: Expression): Expression {
        skip() // a dot
        val where = next()
        val name = readAlpha(where)

        val clazz = left.sig().javaClass(where)
        if (consumeNext(OPEN_CURVE)) {
            // a method call!
            val args = parseArgs()
            expectType(CLOSE_CURVE)

            val argTypes = args.map { it.sig() }

            val method = clazz.methods.find {
                it.name == name
                        && it.parameterCount == args.size
                        && it.parameterTypes
                    .let { pTypes ->
                        pTypes.indices.all { i ->
                            pTypes[i].isAssignableFrom(argTypes[i].javaClass())
                                    || matches(Signature.signFromJavaClass(pTypes[i]), argTypes[i])
                        }
                    }
            } ?: where.error("Cannot find method '$name' on class $clazz")
            return JavaMethodCall(
                where,
                left,
                method,
                args,
                Signature.signFromJavaClass(method.returnType)
            )
        }
        // Oh! It's field access
        val field = clazz.fields.find { it.name == name } ?: where.error("Cannot find field '$name' in class $clazz")
        return JavaFieldAccess(
            where,
            left,
            field,
            Signature.signFromJavaClass(field.type)
        )
    }

    private fun operatorPrecedence(type: Flag) = when (type) {
        Flag.ASSIGNMENT_TYPE -> 1
        Flag.IS -> 2
        Flag.LOGICAL_OR -> 3
        Flag.LOGICAL_AND -> 4
        Flag.BITWISE_OR -> 5
        Flag.BITWISE_AND -> 6
        Flag.EQUALITY -> 7
        Flag.RELATIONAL -> 8
        Flag.BINARY -> 9
        Flag.BINARY_L2 -> 10
        Flag.BINARY_L3 -> 11
        else -> -1
    }

    private fun parseTerm(): Expression {
        // a term is only one value, like 'a', '123'
        when (peek().type) {
            OPEN_CURVE -> {
                skip()
                val expr = parseStatement()
                expectType(CLOSE_CURVE)
                return expr
            }

            else -> {}
        }
        val token = next()
        when {
            token.hasFlag(Flag.VALUE) -> return parseValue(token)
            token.hasFlag(Flag.UNARY) -> return UnaryOperation(token, token.type, parseTerm(), true)
            token.hasFlag(Flag.NATIVE_CALL) -> {
                expectType(OPEN_CURVE)
                val arguments = parseArgs()
                expectType(CLOSE_CURVE)
                return NativeCall(token, token.type, arguments)
            }
        }
        back()
        if (canParseNext()) return parseStatement()
        return token.error("Unexpected token")
    }

    private fun parseValue(token: Token): Expression {
        return when (token.type) {
            NIL -> NilLiteral(token)
            E_TRUE, E_FALSE -> BoolLiteral(token, token.type == E_TRUE)
            E_INT -> IntLiteral(token, token.data.toString().toInt())
            E_FLOAT -> FloatLiteral(token, token.data.toString().toFloat())
            E_DOUBLE -> DoubleLiteral(token, token.data.toString().toDouble())
            E_STRING -> StringLiteral(token, token.data as String)
            E_CHAR -> CharLiteral(token, token.data as Char)
            AT -> parseStruct()
            ALPHA -> verifyAlpha(token)
            CLASS_VALUE -> parseType(token)

            OPEN_CURVE -> {
                val expr = parseStatement()
                expectType(CLOSE_CURVE)
                expr
            }

            else -> token.error("Unknown token type")
        }
    }

    /*
     * @VerticalArrangement {
     *   @Button {
     *     Text: "Accept",
     *     Width: fill_parent,
     *     whenClick: { Notifier1.ShowAlert("thank you") }
     *   }
     *   @Button {
     *      Text: "Decline",
     *      Width: fill_parent,
     *      whenClick: { Notifier1.ShowAlert("why bro") }
     *   }
     * }
     */
    private fun parseStruct(): Struct {
        val name = readAlpha()
        val clazz = Class.forName(COMPONENTS_PACKAGE + name)
        val constructor = clazz.getConstructor(ComponentContainer::class.java)

        val props = ArrayList<Pair<Method, Expression>>()
        // registered events
        // Map < EventName > = Pair < List<ArgSignature>, Expression >
        val events = HashMap<String, Pair<List<Pair<String, Signature>>, Expression>>()
        val children = ArrayList<Struct>()

        val identifier = if (isNext(ALPHA)) readAlpha() else clazz.simpleName + System.currentTimeMillis()

        expectType(OPEN_CURLY)
        while (!isNext(CLOSE_CURLY)) {
            if (consumeNext(AT)) {
                children += parseStruct()
                continue
            }
            val propNameToken = next()
            if (propNameToken.type == WHEN) {
                expectType(DOT)
                // event registration
                val eventName = readAlpha()
                val args = if (isNext(OPEN_CURVE)) readRequiredArgs() else emptyList()
                expectType(COLON)
                val body = parseElement()
                events += eventName to (args to body)
            } else {
                val propName = readAlpha(propNameToken)
                val method = clazz.methods.find { it.name == propName && it.parameterCount == 1 }
                    ?: propNameToken.error("Could not find property name '$propName' on component '$name'")
                expectType(COLON)
                val propValue = parseStatement()
                props += method to propValue

            }
            if (!consumeNext(COMMA)) break
        }
        expectType(CLOSE_CURLY)

        return Struct(
            identifier,
            name,
            constructor,
            props,
            events,
            children
        )
    }

    private fun verifyAlpha(token: Token): Expression {
        val name = readAlpha(token)
        val vrReference = manager.resolveVr(name)
        return if (vrReference == null) {
            // could be a function call or static invocation
            if (manager.hasFunctionNamed(name))
            // there could be multiple functions with same name
            // but different args, this just marks it as a function
                Alpha(token, -3, name, Sign.NONE)
            else if (manager.staticClasses.contains(name))
            // probably referencing a method from an outer class
                Alpha(token, -2, name, Sign.NONE)
            else {
                val envObj = executor.javaObjMap[name]
                if (envObj != null) {
                    // jaa objects were injected :)
                    JavaName(token, name, false, ClassSign(executor.knownJavaClasses[name]!!))
                } else {
                    val staticClass = executor.knownJavaClasses[name]
                    if (staticClass != null) {
                        JavaName(token, name, true, ClassSign(executor.knownJavaClasses[name]!!))
                    } else {
                        // Unresolved name
                        token.error("Cannot find symbol '$name'")
                    }
                }
            }
        } else {
            // classic variable access
            Alpha(token, vrReference.index, name, vrReference.signature)
        }
    }

    private fun parseType(token: Token): TypeLiteral {
        expectType(DOUBLE_COLON)
        return TypeLiteral(token, readSignature(next()))
    }

    private fun unitCall(unitExpr: Expression): Expression {
        if (unitExpr !is Alpha) {
            val message = "Expected a function name for method call, bug got type ${unitExpr.sig()}"
            unitExpr.marking?.error<String>(message)
            // fallback message
            throw RuntimeException(message)
        }
        expectType(OPEN_CURVE)
        val arguments = parseArgs()
        expectType(CLOSE_CURVE)
        val name = unitExpr.value
        val fnExpr = manager.resolveFn(name, arguments.size)
        if (fnExpr != null) {
            if (fnExpr.argsSize == -1)
                throw RuntimeException("[Internal] Function args size is not yet set")
            return MethodCall(unitExpr.marking!!, fnExpr, arguments)
        }
        throw RuntimeException("Not handled lol")
    }

    private fun parseArgs(): List<Expression> {
        while (!isEOF() && isNext(CLOSE_CURVE)) return emptyList()
        val expressions = ArrayList<Expression>()
        while (!isEOF()) {
            expressions += parseStatement()
            if (isNext(COMMA)) skip() else break
        }
        return expressions
    }

    private fun readAlpha(): String {
        val token = next()
        if (token.type != ALPHA) return token.error("Expected alpha token got $token")
        return token.data as String
    }

    private fun readAlpha(token: Token) =
        if (token.type == ALPHA) token.data as String
        else token.error("Was expecting an alpha token")

    private fun expectType(type: Type): Token {
        val next = next()
        if (next.type != type)
            next.error<String>("Expected token type $type but got $next")
        return next
    }

    private fun consumeNext(type: Type): Boolean {
        if (isNext(type)) {
            index++
            return true
        }
        return false
    }

    private fun isNext(type: Type) = !isEOF() && peek().type == type

    private fun back() {
        index--
    }

    private fun skip() {
        index++
    }

    private fun next(): Token {
        if (isEOF()) throw RuntimeException("Early EOF")
        return tokens[index++]
    }

    private fun peek(): Token {
        if (isEOF()) throw RuntimeException("Early EOF")
        return tokens[index]
    }

    private fun isEOF() = index == size
}
