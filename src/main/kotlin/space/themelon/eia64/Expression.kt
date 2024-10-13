package space.themelon.eia64

import space.themelon.eia64.analysis.ScopeManager
import space.themelon.eia64.expressions.*
import space.themelon.eia64.expressions.FunctionExpr
import space.themelon.eia64.runtime.Environment
import space.themelon.eia64.signatures.Signature

abstract class Expression {

    interface Visitor<R> {
        fun getVar(name: String, index: Int): R
        fun getInjected(name: String): R

        fun noneExpression(): R
        fun nilLiteral(nil: NilLiteral): R
        fun intLiteral(literal: IntLiteral): R
        fun floatLiteral(literal: FloatLiteral): R
        fun doubleLiteral(literal: DoubleLiteral): R
        fun boolLiteral(literal: BoolLiteral): R
        fun stringLiteral(literal: StringLiteral): R
        fun charLiteral(literal: CharLiteral): R
        fun typeLiteral(literal: TypeLiteral): R
        fun makeList(makeList: MakeList): R
        fun makeDict(makeDict: MakeDictionary): R
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

        fun know(name: String, clazz: Class<*>): R

        fun newJava(newInstance: NewInstance): R
        fun javaFieldAccess(access: JavaFieldAccess): R
        fun javaMethodCall(jCall: JavaMethodCall): R
        fun eventRegistration(registration: EventRegistration): R

        fun yailConversion(yailConversion: YailConversion): R
        fun struct(componentDefinition: ComponentDefinition): R
    }

    abstract fun <R> accept(v: Visitor<R>): R
    abstract fun sig(env: Environment, scope: ScopeManager): Signature
}