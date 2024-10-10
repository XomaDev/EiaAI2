package space.themelon.eia64

import space.themelon.eia64.expressions.*
import space.themelon.eia64.expressions.FunctionExpr
import space.themelon.eia64.signatures.Signature
import space.themelon.eia64.syntax.Token

abstract class Expression(
    val marking: Token? = null,
) {

    interface Visitor<R> {
        fun noneExpression(): R
        fun nilLiteral(nil: NilLiteral): R
        fun intLiteral(literal: IntLiteral): R
        fun floatLiteral(literal: FloatLiteral): R
        fun doubleLiteral(literal: DoubleLiteral): R
        fun boolLiteral(literal: BoolLiteral): R
        fun stringLiteral(literal: StringLiteral): R
        fun charLiteral(literal: CharLiteral): R
        fun typeLiteral(literal: TypeLiteral): R
        fun alpha(alpha: Alpha): R
        fun variable(variable: Variable): R
        fun isStatement(isStatement: IsStatement): R
        fun unaryOperation(expr: UnaryOperation): R
        fun binaryOperation(expr: BinaryOperation): R
        fun expressions(list: ExpressionList): R
        fun expressionBind(bind: ExpressionBind): R
        fun nativeCall(call: NativeCall): R
        fun cast(cast: Cast): R
        fun scope(scope: Scope): R
        fun methodCall(call: MethodCall): R
        fun until(until: Until): R
        fun itr(itr: Itr): R
        fun whenExpr(whenExpr: When): R
        fun forEach(forEach: ForEach): R
        fun forLoop(forLoop: ForLoop): R
        fun interruption(interruption: Interruption): R
        fun ifFunction(ifExpr: IfStatement): R
        fun function(function: FunctionExpr): R

        fun newJava(newInstance: NewInstance): R
        fun javaName(jName: JavaName): R
        fun javaFieldAccess(access: JavaFieldAccess): R
        fun javaMethodCall(jCall: JavaMethodCall): R
        fun eventRegistration(registration: EventRegistration): R

        fun struct(struct: Struct): R
    }

    abstract fun <R> accept(v: Visitor<R>): R
    abstract fun sig(): Signature
}