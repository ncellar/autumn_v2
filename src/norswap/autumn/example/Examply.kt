@file:Suppress("USELESS_ELVIS", "UNUSED_VARIABLE", "UNCHECKED_CAST") // IDEA being bogus
package norswap.autumn.example
import norswap.autumn.*
import norswap.autumn.parsers.*
import norswap.autumn.utils.expandTabs
import norswap.violin.Maybe
import norswap.violin.Some
import norswap.violin.link.LinkList
import norswap.violin.stream.*
import norswap.violin.utils.after
import java.util.HashMap

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

    val buildIndentMap = Parser { ctx ->
        val map = HashMap<Int, IndentEntry>()
        var pos = 0
        ctx.text.split('\n').forEachIndexed { i, str ->
            val wspace = str.takeWhile { it == ' ' || it == '\t' }
            val count = wspace.expandTabs(4).length
            map.put(i, IndentEntry(count, pos + wspace.length))
            pos += str.length + 1
        }
        ctx.state(IndentMap::class).map = map
        Success
    }

    class IndentStack: ValueStack<Int>()

    val Context.indent: IndentEntry /**/ get() = state(IndentMap::class).get(this)
    val Context.istack: IndentStack /**/ get() = state(IndentStack::class)

    val indent = Parser { ctx ->
        val new = ctx.indent.count
        val old = ctx.istack.peek() ?: 0
        if (new > old) Success after { ctx.istack.push(new) }
        else failure(ctx) { "Expecting indentation > $old positions (found: $new positions)" }
    }

    val dedent = Parser { ctx ->
        val new = ctx.indent.count
        val old = ctx.istack.peek() ?: 0
        if (new <= old) Success after { ctx.istack.pop() }
        else failure(ctx) { "Expecting indentation < $old positions (found: $new positions)" }
    }

    val newline = Predicate { ctx -> ctx.indent.end == ctx.pos }

    /// "LEXICAL" //////////////////////////////////////////////////////////////////////////////////

    val idenChar    = CharPred(Char::isJavaIdentifierPart)
    val idenStart   = CharPred(Char::isJavaIdentifierStart)
    val digit       = CharPred(Char::isDigit)

    val iden = Seq(idenStart, idenChar.repeat)
        .leaf { it }

    val int = digit.repeat1
        .leaf { it.toInt() }

    val `"` = "\""

    val string = Seq(+`"`, any.repeat, +`"`)
        .leaf { it.removeSurrounding(`"`, `"`) }

    /// TYPES //////////////////////////////////////////////////////////////////////////////////////

    class Type (val name: String, val priv: LinkList<Type>)
    class TypeStack: ValueStack<Type>()
    val Context.types: TypeStack /**/ get() = state(TypeStack::class)

    fun isType(ctx: Context, iden: String): Boolean
        = ctx.types.stream().any { it.name == iden }

    fun priv(ctx: Context, iden: String): LinkList<Type>
        = ctx.types.stream().filter { it.name == iden }.next() ?.priv ?: LinkList()

    fun NewType (child: Parser, alias: Boolean = false) = Parser { ctx ->
        child.parse(ctx) andDo {
            val name = ctx.stack.peek() as String
            ctx.types.push(Type(name, if (alias) priv(ctx, name) else LinkList()))
    }   }

    fun Scoped(body: Parser) = Parser { ctx ->
        val size = ctx.types.size
        body.parse(ctx) andDo { ctx.types.truncate(size) }
    }

    fun ClassDef (superclass: Parser, body: Parser) = Parser { ctx ->
        superclass.parse(ctx) // always succeeds
        val parent = ctx.stack.peek() as Maybe<String>
        val name = ctx.stack.at(1) as String
        val snapshot = ctx.types.snapshot()
        if (parent is Some<String>)
            priv(ctx, parent.value.name).stream().each { ctx.types.push(it) }
        body.parse(ctx) andDo {
            ctx.types.pop()
            ctx.types.push(Type(name, ctx.types.diff(snapshot)))
    }   }

    val classGuard = Seq(iden, Predicate { ctx -> isType(ctx, ctx.stack.peek() as String) }).ahead

    ////////////////////////////////////////////////////////////////////////////////////////////////

    val identifier = iden
        .build { Identifier(get()) }

    val qualIden = (iden around1 +".")
        .collect<String>()

    val simpleType = iden
        .build { String(get()) }

    val funTypeParams = Seq(+"(", !"type" around +",", +")")
        .collect<Type>()

    val funType = Seq(funTypeParams, +"->", !"type")
        .build { FunType(get(), get()) }

    val type = !Choice(simpleType, funType)

    val typedIden = Seq(iden, Seq(+":", type).opt.maybe)
        .build { TypedIdentifier(get(), maybe()) }

    val paramDecls = (typedIden around +",")
        .collect<TypedIdentifier>()

    val parenParamDecls = Seq(+"(", paramDecls, +")")

    val lambdaParams = Seq(paramDecls, +"->")

    val paramList = Seq(+"(", !"expr" around +",", +")")
        .collect<Expr>()

    val statements = Seq(indent, Scoped(!"statement" until dedent))
        .collect<Stmt>()

    val decls = Seq(indent, !"decl" until dedent)
        .build { rest<Decl>() }

    val funCall = Seq(iden, paramList)
        .build { FunCall(get(), get()) }

    val ctorCall = Seq(simpleType, paramList)
        .build { CtorCall(get(), get(), null) }

    val methodCall = Seq(!"expr", +".", funCall)
        .build { MethodCall(get(), get()) }

    val fieldAccess = Seq(!"expr", +".", iden)
        .build { FieldAccess(get(), get()) }

    val sum = Seq(!"expr", +"+", !"expr")
        .build { Sum(get(), get()) }

    val diff = Seq(!"expr", +"-", !"expr")
        .build { Diff(get(), get()) }

    val assign = Seq(!"expr", +"=", !"expr") // right-assoc
        .build { Assign(get(), get()) }

    val parenExpr = Seq(+"(", !"expr", +")")

    val lambda = Seq(+"{", lambdaParams.opt.maybe, !"expr", +"}")
        .build { Lambda(maybe() ?: emptyList(), listOf(Return(get()))) }

    val thisCall = Seq(+"this", paramList)
        .build { ThisCall(get()) }

    val `this` = (+"this")
        .build { This }

    val superCall = Seq(+"super", paramList)
        .build { SuperCall(get()) }

    val call = Choice(Seq(classGuard, ctorCall), funCall)

    val expr = !Choice(
        methodCall, fieldAccess, sum, diff, assign, parenExpr, lambda, thisCall,
        `this`, superCall, call, identifier, string, int)

    val `if` = Seq(+"if", expr, statements)
        .build { If(get(), get()) }

    val `while`= Seq(+"while", expr, statements)
        .build { While(get(), get()) }

    val `return` = Seq(+"return", expr, newline)
        .build { Return(get()) }

    val `break` = Seq(+"break", newline)
        .build { Break }

    val `continue` = Seq(+"continue", newline)
        .build { Continue }

    val skip = Seq(+"skip", newline)
        .build { Skip }

    val blockFunCall = Seq(funCall, +":", lambdaParams.opt.maybe, statements)
        .build {
            val call = get<FunCall>()
            val lambda = Lambda(maybe() ?: emptyList(), get())
            call.copy(params = call.params + lambda)
        }

    val blockMethodCall = Seq(expr, +".", blockFunCall)
        .build { MethodCall(get(), get()) }

    val blockCtor = Seq(classGuard, simpleType, paramList, +":", Scoped(decls))
        .build { CtorCall(get(), get(), get()) }

    val blockCall = Choice(blockCtor, blockMethodCall, blockFunCall)

    val varRight = Seq(+"=", Choice(blockCall, expr)).opt.maybe

    val `val` = Seq(+"val", typedIden, varRight, newline)
        .build { Val(get(), maybe()) }

    val `var` = Seq(+"var", typedIden, varRight, newline)
        .build { Var(get(), maybe()) }

    val superAndBody = Scoped(ClassDef(simpleType.opt.maybe, decls))

    val `class` = Seq(+"class", NewType(iden), +":", superAndBody)
        .build { Class(get(), maybe(), get()) }

    val alias = Seq(+"alias", NewType(simpleType, alias = true), +"=", type)
        .build { Alias(get(), get()) }

    val `fun` = Seq(+"fun", iden, parenParamDecls, +":", type, statements)
        .build { Fun(get(), get(), get(), get()) }

    val constructor = Seq(+"constructor", parenParamDecls, statements)
        .build { Constructor(get(), get()) }

    val blockAssign = Seq(identifier, +"=", blockCall)
        .build { Assign(get(), get()) }

    val decl = !Choice(`val`, `var`, `fun`, alias, `class`)

    val statement = !Choice(
            `if`, `while`, `break`, `continue`, `return`, skip,
            blockAssign, decl, blockCall, Seq(expr, newline))

    val pkgString = (iden around +".")
        .collect<String>()

    val import = Seq(+"import", pkgString, +":", NewType(iden))
        .build { Import(get(), get()) }

    val imports = import.repeat
        .collect<Import>()

    val classes = `class`.repeat
        .collect<Class>()

    override val root = Scoped(Seq(whitespace, buildIndentMap, imports, classes))
        .build { File(get(), get()) }
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
data class FieldAccess (val left: Expr, val name: String): Expr
data class Sum (val left: Expr, val right: Expr): Expr
data class Diff (val left: Expr, val right: Expr): Expr
data class Assign (val left: Expr, val right: Expr): Expr
data class Lambda (val params: List<TypedIdentifier>, val body: List<Stmt>): Expr
data class ThisCall (val params: List<Expr>): Expr
    object This: Expr
data class SuperCall (val params: List<Expr>): Expr
data class FunCall (val name: String, val params: List<Expr>): Expr
data class CtorCall (val klass: String, val params: List<Expr>, val body: List<Decl>?): Expr
data class Identifier (val name: String): Expr
data class StringLit (val value: String): Expr
data class IntegerLit (val value: Int): Expr

// Types
data class String(val name: String): Type
data class FunType (val params: List<Type>, val returnType: Type): Type

// Helper
data class TypedIdentifier(val iden: String, val type: Type?): Node

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
    val name: String,
    val params: List<TypedIdentifier>,
    val returnType: Type,
    val body: List<Stmt>
): Stmt, Decl
data class Alias (val defined: String, val aliased: Type): Decl
data class Constructor (val params: List<TypedIdentifier>, val body: List<Stmt>): Decl
data class Class (val name: String, val superclass: String?, val body: List<Decl>): Decl

// Top-Level
data class Import (val pkg: List<String>, val klass: String): Node
data class File (val imports: List<Import>, val classes: List<Class>): Node
