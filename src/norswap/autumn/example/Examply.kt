@file:Suppress("USELESS_ELVIS", "UNUSED_VARIABLE") // IDEA being bogus
package norswap.autumn.example
import norswap.autumn.*
import norswap.autumn.parsers.*
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

    val newline = Predicate { indent.end == pos }

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

    data class Scope (
        val name: String?,
        val parent: Scope?,
        val closed: Boolean = false,
        val map: Map<String, Scope> = emptyMap())

    class ScopeStack: ValueStack<Scope>()
    val Context.scopes: ScopeStack /**/ get() = state(ScopeStack::class)

    // TODO
    @Suppress("unused", "UNUSED_PARAMETER")
    fun <K, V> Map<K, V>.put(k: K, v: V): Map<K, V> = TODO()

    fun Context.scope(name: String?): Scope?
        =   if (name == null) null
            else scopes.stream()
                .filterMap { it.map[name] }
                .next()
            ?: scopes.peek()
                ?.transitive { it.parent }
                ?.filterMap { it.map[name] }
                ?.next()

    fun Context.register(name: String, scope: Scope? = Scope(name, null, false, emptyMap())) {
        if (scope != null) {
            val old = scopes.pop()!!
            scopes.push(old.copy(map = old.map.put(name, scope)))
        }
    }

    fun DefClass(sig: Parser, body: Parser) = Seq(
        sig.withStack(false) {
            val name: String = get()
            val parent: SimpleType? = maybe()
            ctx.register(name)
            val parentScope = ctx.scope(parent?.name)
            if (parentScope != null && !parentScope.closed)
                parser.failure(ctx) { "A class can't inherit one of its outer classes" }
            ctx.scopes.push(Scope(name, parentScope))
            Success
        },
        body,
        DoWithContext {
            val closed = scopes.pop()!!
            register(closed.name!!, closed.copy(closed = true))
        })

    fun Scoped(body: Parser) = Seq(
        DoWithContext { scopes.push(Scope(null, null)) },
        body,
        DoWithContext { scopes.pop() })

    fun Import(import: Parser)
        = import.doWithStack(false) { ctx.register(get<Import>().components.last()) }

    fun Alias(alias: Parser)
        = alias.doWithStack(false) {
            val a = get<Alias>()
            val defined = a.defined.name
            if (a.aliased is SimpleType)
                ctx.register(defined, ctx.scope(a.aliased.name))
            else
                ctx.register(defined, Scope(defined, null, true))
        }

    val classGuard = iden.withStack { parser.succeed(ctx) { ctx.scope(get()) != null } }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    val identifier = iden
        .build { Identifier(get()) }

    val qualIden = (iden around1 +".")
        .collect<String>()

    val simpleType = iden
        .build { SimpleType(get()) }

    val funTypeParams = (!"type" around +",")
        .collect<Type>()

    val funType = Seq(+"(", funTypeParams, +")", +"->", !"type")
        .build { FunType(get(), get()) }

    val type = Choice(simpleType, funType)

    val typedIden = Seq(iden, Seq(+":", type).buildOpt)
        .build { TypedIdentifier(get(), maybe()) }

    val paramDecls = (typedIden around +",")
        .collect<TypedIdentifier>()

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

    val lambda = Seq(+"{", lambdaParams.buildOpt, !"expr", +"}")
        .build { Lambda(maybe() ?: emptyList(), listOf(Return(get()))) }

    val thisCall = Seq(+"this", paramList)
        .build { ThisCall(get()) }

    val `this` = (+"this")
        .build { This }

    val superCall = Seq(+"super", paramList)
        .build { SuperCall(get()) }

    val call = Choice(Seq(classGuard, ctorCall), funCall)

    val expr = Choice(
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

    val blockFunCall = Seq(funCall, +":", lambdaParams.buildOpt, statements)
        .build {
            val call = get<FunCall>()
            val lambda = Lambda(maybe() ?: emptyList(), get())
            call.copy(params = call.params + lambda)
        }

    val blockMethodCall = Seq(expr, +".", blockFunCall)
        .build { MethodCall(get(), get()) }

    val blockCtor = Seq(simpleType, classGuard, paramList, +":", Scoped(decls))
        .build { CtorCall(get(), get(), get()) }

    val blockCall = Choice(Seq(classGuard, blockCtor), blockMethodCall, blockFunCall)

    val `val` = Seq(+"val", typedIden, Seq(+"=", Choice(blockCall, expr)).buildOpt, newline)
        .build { Val(get(), maybe()) }

    val `var` = Seq(+"var", typedIden, Seq(+"=", Choice(blockCall, expr)).buildOpt, newline)
        .build { Var(get(), maybe()) }

    val `class` = DefClass(Seq(+"class", iden, Seq(+":", simpleType).buildOpt), decls)
        .build { Class(get(), maybe(), get()) }

    val alias = Seq(+"alias", simpleType, +"=", type)
        .build { Alias(get(), get()) }

    val `fun` = Seq(
            (+"static").asBool, +"fun", iden, +"(", paramDecls, +")",
            +":", type, statements)
        .build { Fun(get(), get(), get(), get(), get()) }

    val constructor = Seq(+"constructor", +"(", paramDecls, +")", statements)
        .build { Constructor(get(), get()) }

    val blockAssign = Seq(identifier, +"=", blockCall)
        .build { Assign(get(), get()) }

    val decl = Choice(`val`, `var`, `fun`, alias, `class`)

    val statement = Choice(
        `if`, `while`, `break`, `continue`, `return`, skip,
        blockAssign, decl, blockCall,  Seq(expr, newline))

    val import = Import(Seq(+"import", qualIden))
        .build { Import(get()) }

    override val root = Scoped(Seq(
            whitespace,
            buildIndentMap,
            ZeroMore(import).collect<Import>(),
            ZeroMore(`class`).collect<Class>()))
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
data class CtorCall (val klass: SimpleType, val params: List<Expr>, val body: List<Decl>?): Expr
data class Identifier (val name: String): Expr
data class StringLit (val value: String): Expr
data class IntegerLit (val value: Int): Expr

// Types
data class SimpleType (val name: String): Type
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
    val static: Boolean,
    val name: String,
    val params: List<TypedIdentifier>,
    val returnType: Type,
    val body: List<Stmt>
): Stmt, Decl
data class Alias (val defined: SimpleType, val aliased: Type): Decl
data class Constructor (val params: List<TypedIdentifier>, val body: List<Stmt>): Decl
data class Class (val name: String, val superclass: SimpleType?, val body: List<Decl>): Decl

// Top-Level
data class Import (val components: List<String>): Node
data class File (val imports: List<Import>, val classes: List<Class>): Node
