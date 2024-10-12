package space.themelon.eia64.expressions

import space.themelon.eia64.Expression
import space.themelon.eia64.signatures.Sign

data class Statements(
  val expressions: List<Expression>
): Expression() {

  override fun <R> accept(v: Visitor<R>): R {
    TODO("Not yet implemented")
  }

  override fun sig() = Sign.NONE
}