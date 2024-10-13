package space.themelon.eia64.analysis

import space.themelon.eia64.syntax.Token
import space.themelon.eia64.syntax.Type
import space.themelon.eia64.syntax.Type.ALPHA

class BlockParser(private val tokens: List<Token>) {

  var packageName: String = ""

  val functions = ArrayList<Triple<String, List<String>, Boolean>>()
  val properties = ArrayList<Triple<String, List<String>, Boolean>>()
  val events = ArrayList<Pair<String, List<String>>>()

  private var index = 0
  private var size = tokens.size

  init {
    while (notEOF()) {
      val token = next()
      when (token.type) {
        Type.BLOCK -> functions += parseFun()
        Type.PROPERTY -> properties += parseFun()
        Type.EVENT -> events += parseFun().let { it.first to it.second }
        else -> { }
      }
    }
  }

  private fun parseFun(): Triple<String, List<String>, Boolean> {
    val name = eat(ALPHA).data as String
    val argNames = ArrayList<String>()
    eat(Type.OPEN_CURVE)
    while (!isNext(Type.CLOSE_CURVE)) {
      index++ // we dont need to know signature
      eat(Type.COMMA)
      argNames += eat(ALPHA).data as String
    }
    eat(Type.CLOSE_CURVE)
    val returning = consume(Type.COLON)
    if (returning) index++
    return Triple(name, argNames, returning)
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

  private fun isNext(type: Type) = notEOF() && peek().type == type

  private fun peek() = if (isEOF()) throw RuntimeException("Early EOF") else tokens[index]
  private fun next() = if (isEOF()) throw RuntimeException("Early EOF") else tokens[index++]

  private fun notEOF() = index < size
  private fun isEOF() = index >= size
}