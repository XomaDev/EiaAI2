package space.themelon.eia64.analysis

import com.google.appinventor.components.runtime.ComponentContainer
import com.google.appinventor.components.runtime.util.YailDictionary
import com.google.appinventor.components.runtime.util.YailList
import space.themelon.eia64.Expression
import space.themelon.eia64.expressions.*
import space.themelon.eia64.runtime.Environment
import space.themelon.eia64.signatures.*
import space.themelon.eia64.signatures.Matching.matches
import space.themelon.eia64.syntax.Flag
import space.themelon.eia64.syntax.Token
import space.themelon.eia64.syntax.Type
import space.themelon.eia64.syntax.Type.*
import java.lang.reflect.Method

class Parser(
  private val executor: Environment,
) {

  private var manager = ScopeManager()
  private val javaClasses = HashMap<String, Class<*>>()

  private lateinit var tokens: List<Token>
  private var index = 0
  private var size = 0

  private lateinit var parsed: ExpressionList

  fun reset() {
    manager = ScopeManager()
  }

  fun parse(tokens: List<Token>): ExpressionList? {
    index = 0
    size = tokens.size
    this.tokens = tokens

    val expressions = ArrayList<Expression>()
    parseSkeleton()
    while (notEOF()) statement().let { if (it !is DiscardExpression) expressions += it }
    if (expressions.isEmpty()) return null
    if (Environment.DEBUG) expressions.forEach { println(it) }
    parsed = ExpressionList(expressions)
    return parsed
  }

  // make sure to update canParseNext() when we add stuff here!
  private fun statement(): Expression {
    val token = next()
    if (token.flags.isNotEmpty()) {
      when (token.flags[0]) {
        Flag.LOOP -> return loop(token)
        Flag.INTERRUPTION -> return interruption(token)
        else -> {}
      }
    }
    return when (token.type) {
      KNOW -> knowSmt()
      IF -> ifSmt(token)
      FUN, EVENT, PROPERTY, BLOCK -> funSmt(token.type)
      NEW -> newSmt(token)
      LET -> return variableDeclaration(token)
      else -> {
        index--
        parseExpr(0)
      }
    }
  }

  private fun canParseNext(): Boolean {
    val token = peek()
    if (token.flags.isNotEmpty())
      token.flags[0].let {
        if (it == Flag.LOOP || it == Flag.INTERRUPTION)
          return true
      }
    return when (token.type) {
      KNOW,
      IF,
      FUN, EVENT, PROPERTY, BLOCK,
      LET,
      NEW -> true

      else -> false
    }
  }

  private fun knowSmt(): Know {
    if (consume(OPEN_CURVE)) {
      val shortName = eat(ALPHA).data as String
      eat(COMMA)
      return Know(readPackage(), shortName)
    }
    return Know(readPackage(), null)
  }

  private fun readPackage(): String {
    val pkgName = StringBuilder()
    pkgName.append(eat(ALPHA))

    while (consume(DOT)) pkgName.append(eat(ALPHA))
    return pkgName.toString()
  }

  private fun parseSkeleton() {
    // We'll be bumping Indexes, so save it to set back later
    val originalIndex = index

    var curlyBracesCount = 0

    fun handleFn() {
      val reference = functionOutline()
      manager.defineSemiFn(reference.name, reference)
    }

    while (notEOF()) {
      val token = next()
      when (token.type) {
        OPEN_CURLY -> curlyBracesCount++

        CLOSE_CURLY -> {
          if (curlyBracesCount == 0) break
          else curlyBracesCount--
        }

        FUN, EVENT, PROPERTY, BLOCK -> if (curlyBracesCount == 0) handleFn()
        else -> {}
      }
    }

    index = originalIndex
  }

  private fun functionOutline(): FunctionReference {
    val where = eat(ALPHA)

    val requiredArgs = argSignatures()

    val isVoid: Boolean
    val returnSignature = if (isNext(COLON)) {
      index++
      isVoid = false
      readSignature(next())
    } else {
      isVoid = true
      Sign.UNIT
    }

    return FunctionReference(
      where,
      where.data as String,
      null,
      requiredArgs,
      requiredArgs.size,
      returnSignature,
      isVoid,
      index
    )
  }

  // this function was repurposed for making java instances
  private fun newSmt(token: Token): Expression {
    val name = eat(ALPHA).data as String
    val clazz = executor.classInjections[name]
      ?: token.error("Cannot find symbol '$name'")
    eat(OPEN_CURVE)
    val args = args()
    eat(CLOSE_CURVE)

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

  private fun loop(where: Token): Expression {
    when (where.type) {
      UNTIL -> {
        val expr = between(OPEN_CURVE, CLOSE_CURVE) { statement() }
        // Scope: Automatic
        val body = manager.iterativeScope { smtOrBody() }
        return Until(where, expr, body)
      }

      FOR -> {
        // we cannot expose initializers outside the for loop
        eat(OPEN_CURVE)
        return if (isNext(ALPHA)) forEach(where) else forVariableLoop(where)
      }

      EACH -> {
        eat(OPEN_CURVE)
        val iName = eat(ALPHA).data as String
        eat(COLON)

        val from = statement()
        eat(TO)
        val to = statement()

        var by: Expression? = null
        if (isNext(BY)) {
          index++
          by = statement()
        }
        eat(CLOSE_CURVE)
        manager.enterScope()
        manager.defineVariable(iName, Sign.INT)
        // Manual Scopped!
        val body = manager.iterativeScope { manualSmtBody() }
        manager.leaveScope()
        return Itr(where, iName, from, to, by, body)
      }

      else -> return where.error("Unknown loop type symbol")
    }
  }

  private fun forEach(where: Token): ForEach {
    val iName = eat(ALPHA).data as String
    eat(IN)
    val entity = statement()
    eat(CLOSE_CURVE)

    val elementSignature = when (entity.sig()) {
      Sign.LIST -> Sign.ANY
      Sign.STRING -> Sign.CHAR

      else -> {
        where.error<String>("Unknown non iterable element for '$iName'")
        throw RuntimeException()
      }
    }

    manager.enterScope()
    manager.defineVariable(iName, elementSignature)
    // Manual Scopped!
    val body = manager.iterativeScope { manualSmtBody() }
    manager.leaveScope()
    return ForEach(where, iName, entity, body)
  }

  private fun forVariableLoop(
    where: Token,
  ): ForLoop {
    manager.enterScope()
    val initializer = if (isNext(SEMI_COLON)) null else statement()
    eat(SEMI_COLON)
    val conditional = if (isNext(SEMI_COLON)) null else statement()
    eat(SEMI_COLON)
    val operational = if (isNext(CLOSE_CURVE)) null else statement()
    eat(CLOSE_CURVE)
    // double layer scope wrapping
    // Scope: Automatic
    val body = manager.iterativeScope { smtOrBody() }
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
            val expr = statement()
            val gotSignature = expr.sig()
            if (!matches(expectedSignature, gotSignature)) {
              token.error<String>("Was expecting return type of $expectedSignature but got $gotSignature")
              throw RuntimeException()
            }
            expr
          }
        }

        USE -> statement()
        else -> null
      }
    )
  }

  private fun argSignatures(): List<Pair<String, Signature>> {
    eat(OPEN_CURVE)
    val requiredArgs = mutableListOf<Pair<String, Signature>>()
    while (notEOF() && peek().type != CLOSE_CURVE) {
      val parameterName = eat(ALPHA).data as String
      eat(COLON)
      val signature = readSignature(next())

      requiredArgs += parameterName to signature
      if (!isNext(COMMA)) break
      index++
    }
    eat(CLOSE_CURVE)
    return requiredArgs
  }

  private fun funSmt(type: Type): FunctionExpr {
    val reference = manager.readFnOutline()
    index = reference.tokenIndex
    manager.enterScope()
    reference.parameters.forEach { manager.defineVariable(it.first, it.second) }

    val body: Expression = if (isNext(ASSIGNMENT)) {
      index++
      statement()
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


  private fun ifSmt(where: Token): IfStatement {
    val condition = between(OPEN_CURVE, CLOSE_CURVE) { statement() }
    val thenBody = smtOrBody()

    val elseBody = if (notEOF() && consume(ELSE))
      peek().let { if (it.type == IF) ifSmt(it) else statement() }
    else null

    return IfStatement(
      where,
      condition,
      thenBody,
      elseBody
    )
  }

  // automatic scope operator
  private fun smtOrBody(): Scope {
    manager.enterScope()
    if (isNext(OPEN_CURLY)) {
      val body = Scope(expressions(), manager.leaveScope())
      return body
    }
    return Scope(statement(), manager.leaveScope())
  }

  // scope is manually operated
  private fun manualSmtBody() = if (isNext(OPEN_CURLY)) expressions() else statement()

  private fun expressions(): Expression {
    eat(OPEN_CURLY)
    parseSkeleton()
    val expressions = ArrayList<Expression>()
    while (notEOF() && !consume(CLOSE_CURLY))
      expressions.add(statement())
    return ExpressionList(expressions)
  }

  private fun variableDeclaration(where: Token): Expression {
    val expressions = mutableListOf<Expression>()
    do {
      // read minimum one declaration
      expressions += readVariableDeclaration(where)
      //println("Iteration: " + expressions.last())
    } while (isNext(COMMA).also { if (it) next() })

    if (expressions.size == 1) return expressions.first()
    return ExpressionBind(expressions)
  }

  private fun readVariableDeclaration(
    where: Token,
  ): Expression {
    val name = eat(ALPHA).data as String

    val expr: Expression
    val signature: Signature

    if (!isNext(COLON)) {
      val assignmentExpr = readVariableExpr()
      signature = assignmentExpr.sig()
      expr = Variable(where, name, assignmentExpr)
    } else {
      index++
      signature = readSignature(next())
      expr = Variable(
        where,
        name,
        readVariableExpr(),
        signature
      )
    }
    manager.defineVariable(name, signature)
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
        E_JAVA -> Sign.JAVA
        else -> token.error("Unknown class $classType")
      }
    }

    if (token.type != ALPHA) {
      token.error<String>("Expected a class type")
      // end of execution
    }
    val name = token.data as String
    val knownClass = executor.classInjections[name]
    if (knownClass != null) return ClassSign(knownClass)
    token.error<String>("Unknown class ${token.data}")
    throw RuntimeException()
  }

  private fun readVariableExpr(): Expression {
    val nextToken = peek()
    return when (nextToken.type) {
      ASSIGNMENT -> {
        index++
        statement()
      }

      else -> nextToken.error("Unexpected variable expression")
    }
  }

  private fun parseExpr(minPrecedence: Int): Expression {
    // this parses a full expressions, until it's done!
    var left = parseElement()
    if (notEOF() && peek().hasFlag(Flag.POSSIBLE_RIGHT_UNARY)) {
      val where = next()
      left = UnaryOperation(where, where.type, left, false)
    }
    while (notEOF()) {
      val opToken = peek()
      if (!opToken.hasFlag(Flag.OPERATOR)) return left

      val precedence = operatorPrecedence(opToken.flags[0])
      if (precedence == -1) return left

      if (precedence < minPrecedence) return left

      index++ // operator token
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
    while (notEOF()) {
      val nextOp = peek()
      if (nextOp.type != DOT // (left is class) trying to call a method on an object. e.g. person.sayHello()
        && !(nextOp.type == OPEN_CURVE && !isLiteral(left)) // (left points/is a unit)
        && nextOp.type != OPEN_SQUARE // array element access
        && nextOp.type != DOUBLE_COLON // value casting
        && (nextOp.type != COLON) // event registration
      ) break
      if (nextOp.type == COLON && !left.sig().isJava()) break

      when (nextOp.type) {
        // calling shadow func
        OPEN_CURVE -> left = unitCall(left)
        DOUBLE_COLON -> {
          index++
          left = Cast(nextOp, left, readSignature(next()))
        }

        COLON -> {
          index++
          left = eventRegistration(left)
        }

        else -> left = javaCall(left)
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
    val where = eat(ALPHA)
    val eventName = where.data as String
    val requiredArgs = mutableListOf<Pair<String, Signature>>()
    manager.enterScope()
    if (consume(OPEN_CURVE)) {
      while (notEOF() && peek().type != CLOSE_CURVE) {
        val parameterName = eat(ALPHA).data as String
        eat(COLON)
        val signature = readSignature(next())

        manager.defineVariable(parameterName, signature)
        requiredArgs += parameterName to signature
        if (!isNext(COMMA)) break
        index++
      }
      eat(CLOSE_CURVE)
    }
    val body = expressions()
    manager.leaveScope()
    return EventRegistration(where, jExpr, eventName, requiredArgs, body)
  }

  private fun javaCall(left: Expression): Expression {
    index++ // a dot
    val where = eat(ALPHA)
    val name = where.data as String

    val clazz = left.sig().javaClass(where)
    if (consume(OPEN_CURVE)) {
      // a method call!
      val args = args()
      eat(CLOSE_CURVE)

      val argTypes = args.map { it.sig() }
      var result: Method? = null

      val transformedArgs = arrayOfNulls<Expression>(args.size)

      methodSearch@
      for (method in clazz.methods) {
        if (method.name != name || method.parameterCount != args.size) continue@methodSearch
        val expectedTypes = method.parameterTypes
        for (i in expectedTypes.indices) {
          val transformed = ensureParameterCompatibility(args[i], expectedTypes[i], argTypes[i])
            ?: continue@methodSearch
          transformedArgs[i] = transformed
        }
        // all checks were completed
        result = method
        break
      }
      if (result == null) where.error<String>("Cannot find method '$name' in $clazz $argTypes")
      result!!
      return JavaMethodCall(
        where,
        left,
        result,
        args,
        Signature.signFromJavaClass(result.returnType)
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

  private fun ensureParameterCompatibility(value: Expression, expected: Class<*>, gotSign: Signature): Expression? {
    // We need to ensure compatibility for both App Inventor and Java!
    val got = gotSign.javaClass()
    val expectedSign = Signature.signFromJavaClass(expected)

    if (expected.isAssignableFrom(got)) return value
    if (matches(expectedSign, gotSign)) return value

    // we need to interfere and ensure manual interop
    if (expected == YailList::class.java) {
      if (value.sig() != Sign.LIST) return null
      return YailConversion(YailList::class.java, value)
    }
    if (expected == YailDictionary::class.java) {
      if (value.sig() != Sign.DICT) return null
      return YailConversion(YailDictionary::class.java, value)
    }
    return null
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
    val token = next()
    val type = token.type
    when {
      type == OPEN_CURVE -> {
        val expr = statement()
        eat(CLOSE_CURVE)
        return expr
      }

      type == MAKE_LIST -> return makeList(token)
      type == MAKE_DICT -> return makeDict(token)
      token.hasFlag(Flag.VALUE) -> return parseValue(token)
      // TODO:
      //  Note: it previously used to call parseTerm() but we changed to parseElement()
      /// Just remember this if something goes wrong while parsing the syntax!
      token.hasFlag(Flag.UNARY) -> return UnaryOperation(token, token.type, parseElement(), true)
      token.hasFlag(Flag.NATIVE_CALL) -> {
        eat(OPEN_CURVE)
        val arguments = args()
        eat(CLOSE_CURVE)
        return NativeCall(token, token.type, arguments)
      }
    }
    index--
    if (canParseNext()) return statement()
    return token.error("Unexpected token")
  }

  private fun makeList(where: Token): MakeList {
    eat(OPEN_CURVE)
    val elements = args()
    eat(CLOSE_CURVE)
    return MakeList(where, elements)
  }

  private fun makeDict(where: Token): MakeDictionary {
    val elements = ArrayList<Pair<Expression, Expression>>()
    eat(OPEN_CURVE)
    while (notEOF() && !isNext(CLOSE_CURVE)) {
      val key = statement()
      eat(COLON)
      val value = statement()
      elements += key to value
      if (!consume(COMMA)) break
    }
    eat(CLOSE_CURVE)
    return MakeDictionary(where, elements)
  }

  private fun parseValue(token: Token): Expression {
    return when (token.type) {
      NIL -> NilLiteral()
      E_TRUE, E_FALSE -> BoolLiteral(token, token.type == E_TRUE)
      E_INT -> IntLiteral(token, token.data.toString().toInt())
      E_FLOAT -> FloatLiteral(token, token.data.toString().toFloat())
      E_DOUBLE -> DoubleLiteral(token, token.data.toString().toDouble())
      E_STRING -> StringLiteral(token, token.data as String)
      E_CHAR -> CharLiteral(token, token.data as Char)
      AT -> parseStruct()
      ALPHA -> Alpha(token)
      CLASS_VALUE -> parseType(token)

      OPEN_CURVE -> {
        val expr = statement()
        eat(CLOSE_CURVE)
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
    val name = eat(ALPHA).data as String
    val clazz = Class.forName(name)
    val constructor = clazz.getConstructor(ComponentContainer::class.java)

    val props = ArrayList<Pair<Method, Expression>>()
    // registered events
    // Map < EventName > = Pair < List<ArgSignature>, Expression >
    val events = HashMap<String, Pair<List<Pair<String, Signature>>, Expression>>()
    val children = ArrayList<Struct>()

    var parent: Expression? = null
    if (consume(OPEN_CURVE)) {
      parent = statement()
      eat(CLOSE_CURVE)
    }

    val identifier = if (isNext(ALPHA)) eat(ALPHA).data as String else clazz.simpleName + System.currentTimeMillis()

    eat(OPEN_CURLY)
    while (!isNext(CLOSE_CURLY)) {
      if (consume(AT)) {
        children += parseStruct()
        continue
      }
      if (consume(WHEN)) {
        eat(DOT)
        // event registration
        val eventName = eat(ALPHA).data as String
        val args = if (isNext(OPEN_CURVE)) argSignatures() else emptyList()
        eat(COLON)
        val body = parseElement()
        events += eventName to (args to body)
      } else {
        val propNameToken = eat(ALPHA)
        val propName = propNameToken.data as String
        val method = clazz.methods.find { it.name == propName && it.parameterCount == 1 }
          ?: propNameToken.error("Could not find property name '$propName' on component '$name'")
        eat(COLON)
        val propValue = statement()
        props += method to propValue

      }
      if (!consume(COMMA)) break
    }
    eat(CLOSE_CURLY)

    return Struct(
      identifier,
      parent,
      name,
      constructor,
      props,
      events,
      children
    )
  }

  private fun parseType(token: Token): TypeLiteral {
    eat(DOUBLE_COLON)
    return TypeLiteral(token, readSignature(next()))
  }

  private fun unitCall(unitExpr: Expression): Expression {
    if (unitExpr !is Alpha) {
      val message = "Expected a function name for method call, bug got type ${unitExpr.sig()}"
      // fallback message
      throw RuntimeException(message)
    }
    eat(OPEN_CURVE)
    val arguments = args()
    eat(CLOSE_CURVE)
    val name = unitExpr.value
    val fnExpr = manager.resolveFn(name, arguments.size)
    if (fnExpr != null) {
      if (fnExpr.argsSize == -1)
        throw RuntimeException("[Internal] Function args size is not yet set")
      return MethodCall(unitExpr.where, fnExpr, arguments)
    }
    throw RuntimeException("Not handled lol")
  }

  private fun args(): List<Expression> {
    while (notEOF() && isNext(CLOSE_CURVE)) return emptyList()
    val expressions = ArrayList<Expression>()
    while (notEOF()) {
      expressions += statement()
      if (isNext(COMMA)) index++ else break
    }
    return expressions
  }

  private fun eat(type: Type) = next().let {
    if (it.type != type) it.error("Expected token type $type but got ${it.type}")
    else it
  }

  private fun consume(type: Type): Boolean {
    if (isNext(type)) {
      index++
      return true
    }
    return false
  }

  private fun <T> between(start: Type, end: Type, block: () -> T): T {
    eat(start)
    val t = block()
    eat(end)
    return t
  }

  private fun isNext(type: Type) = notEOF() && peek().type == type

  private fun peek() = if (isEOF()) throw RuntimeException("Early EOF") else tokens[index]
  private fun next() = if (isEOF()) throw RuntimeException("Early EOF") else tokens[index++]

  private fun notEOF() = index < size
  private fun isEOF() = index >= size
}
