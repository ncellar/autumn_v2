@file:Suppress("USELESS_ELVIS") // IDEA being bogus
package norswap.autumn.example
import norswap.autumn.*
import norswap.autumn.parsers.*
import norswap.violin.stream.any
import norswap.violin.utils.after

object examply: Grammar()
{
    override fun requiredStates() = listOf(IndentMap(), IndentStack())

    /// INDENTATION ////////////////////////////////////////////////////////////////////////////////

    data class IndentEntry (val count: Int, val end: Int)

    class IndentMap: InertState<IndentMap> {
        lateinit var map: Map<Int, IndentEntry>
        fun get(ctx: Context): IndentEntry =
            map[ctx.lineMap.lineFromOffset(ctx.pos)]!!
    }

    class IndentStack: ValueStack<Int>()

    val Context.indent: IndentEntry /**/ get() = state(IndentMap::class).get(this)
    val Context.istack: IndentStack /**/ get() = state(IndentStack::class)

    val indent = Parser { ctx ->
        val new = ctx.indent.count
        val old = ctx.istack.peek() ?: 0
        if (new > old) Success after { ctx.istack.push(new) }
        else ctx.failure { "Expecting indentation > $old positions (found: $new positions)" }
    }

    val dedent = Parser { ctx ->
        val new = ctx.indent.count
        val old = ctx.istack.peek() ?: 0
        if (new <= old) Success after { ctx.istack.pop() }
        else ctx.failure { "Expecting indentation < $old positions (found: $new positions)" }
    }

    val newline = Predicate { indent.end == pos }

    /// TYPES //////////////////////////////////////////////////////////////////////////////////////

    class TypeNames: ValueStack<String>()
    val Context.types: TypeNames /**/ get() = this.state<TypeNames>()

    val defType = Perform { types.push(stack.peek() as String) }
    val classGuard = Predicate { val name = stack.peek() ; types.stream().any { it == name } }

    fun Scope(child: Parser) = Parser { ctx ->
        val types = ctx.types.snapshot()
        child.parse(ctx).after { ctx.types.restore(types) }
    }

    /// "LEXICAL" //////////////////////////////////////////////////////////////////////////////////

    val spaceOrTab  = CharPred { it == ' ' || it == '\t' }
    val idenChar    = CharPred(Char::isJavaIdentifierPart)
    val idenStart   = CharPred(Char::isJavaIdentifierStart)
    val digit       = CharPred(Char::isDigit)

    val iden = Seq(idenStart, ZeroMore(idenChar))
        .leaf { it }

    val int = OneMore(digit)
        .leaf { it.toInt() }

    val string = Seq(+"\"", ZeroMore(AnyChar()), +"\"")
        .leaf { it.removeSurrounding("\"", "\"") }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    val qualIden = Separated(iden, +".")
        .build { QualifiedIdentifier(rest()) }

    val simpleType = qualIden
        .build { SimpleType(it()) }

    val funTypeParams = Optional(Separated(!"type", +","))
        .collect<Type>()

    val funType = Seq(+"(", funTypeParams, +")", +"->", !"type")
        .build { FunType(it(), it()) }

    val type = Choice(simpleType, funType)

    val typedIden = Seq(iden, Seq(+":", type).maybe())
        .build { TypedIdentifier(it(), maybe()) }

    val paramDecls = Optional(Separated(typedIden, +","))
        .collect<TypedIdentifier>()

    val lambdaParams = Seq(paramDecls, +"->")

    val paramList = Seq(+"(", Optional(Separated(!"expr", +",")) , +")")
        .collect<Expr>()

    val statements = Seq(indent, Until(!"statement", dedent))
        .collect<Stmt>()

    val decls = Seq(indent, Until(!"decl", dedent))
        .build { rest<Decl>() }

    val funCall = Seq(iden, paramList)
        .build { FunCall(it(), it()) }

    val ctorCall = Seq(qualIden, paramList)
        .build { CtorCall(it(), it(), null) }

    val methodCall = Seq(!"expr", +".", funCall)
        .build { MethodCall(it(), it()) }

    val fieldAccess = Seq(!"expr", +".", iden)
        .build { FieldAccess(it(), it()) }

    val sum = Seq(!"expr", +"+", !"expr")
        .build { Sum(it(), it()) }

    val diff = Seq(!"expr", +"-", !"expr")
        .build { Diff(it(), it()) }

    val assign = Seq(!"expr", +"=", !"expr") // right-assoc
        .build { Assign(it(), it()) }

    val parenExpr = Seq(+"(", !"expr", +")")

    val lambda = Seq(+"{", lambdaParams.maybe(), !"expr", +"}")
        .build { Lambda(maybe() ?: emptyList(), listOf(Return(it()))) }

    val thisCall = Seq(+"this", paramList)
        .build { ThisCall(it()) }

    val `this` = (+"this")
        .build { This }

    val superCall = Seq(+"super", paramList)
        .build { SuperCall(it()) }

    var expr = Choice(
        methodCall, fieldAccess, sum, diff, assign, parenExpr, lambda, thisCall,
        `this`, superCall, funCall, ctorCall, iden, string, int)

    val `if` = Seq(+"if", expr, statements)
        .build { If(it(), it()) }

    val `while`= Seq(+"while", expr, statements)
        .build { While(it(), it()) }

    val `return` = Seq(+"return", expr, newline)
        .build { Return(it()) }

    val `break` = Seq(+"break", newline)
        .build { Break }

    val `continue` = Seq(+"continue", newline)
        .build { Continue }

    val skip = Seq(+"skip", newline)
        .build { Skip }

    val blockFunCall = Seq(qualIden, paramList, +":", lambdaParams.maybe(), statements)
        .build { FunCall(it(), it<List<Expr>>() + Lambda(maybe() ?: emptyList(), it())) }

    val blockCtor = Seq(qualIden, classGuard, paramList, +":", decls)
        .build { CtorCall(it(), it(), it()) }

    val blockCall = Choice(blockCtor, blockFunCall)

    val `val` = Seq(+"val", typedIden, Seq(+"=", Choice(blockCall, expr)).maybe(), newline)
        .build { Val(it(), maybe()) }

    val `var` = Seq(+"var", typedIden, Seq(+"=", Choice(blockCall, expr)).maybe(), newline)
        .build { Var(it(), maybe()) }

    val `class` = Seq(+"class", iden, defType, Seq(+":", simpleType).maybe(), Scope(decls))
        .build { Class(it(), maybe(), it()) }

    val `fun` = Seq(
            (+"static").asBool(), +"fun", iden, +"(", paramDecls, +")",
            +":", type, Scope(statements))
        .build { Fun(it(), it(), it(), it(), it()) }

    val constructor = Seq(+"constructor", +"(", paramDecls, +")", statements)
        .build { Constructor(it(), it()) }

    val blockAssign = Seq(iden, +"=", blockCall)
        .build { Assign(it(), it()) }

    val decl = Choice(`val`, `var`, `class`, `fun`)

    val statement = Choice(
        `if`, `while`, `break`, `continue`, skip, blockAssign, decl, blockCall,  Seq(expr, newline))

    val import = Seq(+"import", qualIden)
        .build { Import(it()) }

    override val root = Seq(ZeroMore(import).collect<Import>(), ZeroMore(`class`).collect<Class>())
        .build { File(it(), it()) }

    override val status = READY()
}

/// AST ////////////////////////////////////////////////////////////////////////////////////////////

// Interfaces
interface Node
interface Expr: Node
interface Stmt : Node
interface Decl : Node
interface Type: Node

// Expressions
data class MethodCall (val left: Expr, val right: FunCall): Expr
data class FieldAccess (val left: Expr, val name: Identifier): Expr
data class Sum (val left: Expr, val right: Expr): Expr
data class Diff (val left: Expr, val right: Expr): Expr
data class Assign (val left: Expr, val right: Expr): Expr
data class Lambda (val params: List<TypedIdentifier>, val body: List<Stmt>): Expr
data class ThisCall (val params: List<Expr>): Expr
    object This: Expr
data class SuperCall (val params: List<Expr>): Expr
data class FunCall (val name: Identifier, val params: List<Expr>): Expr
data class CtorCall (val klass: SimpleType, val params: List<Expr>, val body: List<Decl>?): Expr
data class Identifier (val name: String): Expr
data class StringLit (val value: String): Expr
data class IntegerLit (val value: Int): Expr

// Types
data class SimpleType (val iden: QualifiedIdentifier): Type
data class FunType (val params: List<Type>, val returnType: Type): Type

// Helpers
data class QualifiedIdentifier (val components: List<Identifier>): Expr
data class TypedIdentifier(val iden: Identifier, val type: Type?): Node

// Statements & Declarations
data class If (val cond: Expr, val body: List<Stmt>): Stmt
data class While (val cond: Expr, val body: List<Stmt>): Stmt
    object Break: Stmt
    object Skip: Stmt
    object Continue: Stmt
data class Return (val expr: Expr): Stmt
data class Val (val iden: TypedIdentifier, val value: Expr?): Stmt, Decl
data class Var (val iden: TypedIdentifier, val value: Expr?): Stmt, Decl
data class Fun (
    val static: Boolean,
    val name: Identifier,
    val params: List<TypedIdentifier>,
    val returnType: Type,
    val body: List<Stmt>
): Stmt, Decl
data class Constructor (val params: List<TypedIdentifier>, val body: List<Stmt>): Decl
data class Class (val name: Identifier, val superclass: SimpleType?, val body: List<Decl>): Decl

// Top-Level
data class Import (val iden: QualifiedIdentifier): Node
data class File (val imports: List<Import>, val classes: List<Class>): Node
