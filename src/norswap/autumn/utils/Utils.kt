package norswap.autumn.utils
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.reflect.KClass

// Miscellaneous utilities.

// -------------------------------------------------------------------------------------------------

/**
 * Returns a version of this string where all tabs have been fully expanded, so that each tab
 * brings the column (counted from 0 starting at each newline) to a multiple of [tabSize].
 */
// Courtesy of http://stackoverflow.com/a/34933524/298664
fun String.expandTabsToBuilder(tabSize: Int): StringBuilder
{
    val b = StringBuilder()
    var col = 0
    for (c in this) when (c) {
        '\n' -> {
            b += c
            col = 0
        }
        '\t' -> {
            val spaces = tabSize - col % tabSize
            repeat(spaces) { b += " " }
            col += spaces
        }
        else -> {
            b += c
            ++col
    }   }
    return b
}

// -------------------------------------------------------------------------------------------------

/**
 * Returns a version of this string where all tabs have been fully expanded, so that each tab
 * brings the column (counted from 0 starting at each newline) to a multiple of [tabSize].
 */
fun String.expandTabs(tabSize: Int): String
    = expandTabsToBuilder(tabSize).toString()

// -------------------------------------------------------------------------------------------------

/**
 * Expands tabs (like [expandTabs]) and add a null character at the end.
 */
fun String.expandTabsAndNullTerminate(tabSize: Int): String {
    val b = expandTabsToBuilder(tabSize)
    b += '\u0000'
    return b.toString()
}

// -------------------------------------------------------------------------------------------------

/**
 * Indicates if the element indicates a call within the given class and method.
 */
fun StackTraceElement.isMethod(klass: Class<*>, method: String)
    =  klass.isAssignableFrom(Class.forName(this.className))
    && method == this.methodName

// -------------------------------------------------------------------------------------------------

/**
 * Indicates if the element indicates a call within the given class and method.
 */
fun StackTraceElement.isMethod(klass: KClass<*>, method: String)
    = isMethod(klass.java, method)


// -------------------------------------------------------------------------------------------------

/**
 * A string that describes this stack trace element, in a format recognized by IntelliJ,
 * for which it generates *correct* clickable links (important when inlining is involved).
 */
fun StackTraceElement.clickableString()
    = "at " + this

// -------------------------------------------------------------------------------------------------

/**
 * Return a string describing the source code location of the stack trace element.
 * Extracted from [StackTraceElement.toString].
 */
fun StackTraceElement.location(): String = run {
    if (fileName == null) "(Unknown Source)"
    else if (lineNumber >= 0) "($fileName:$lineNumber)"
    else "($fileName)"
}

// -------------------------------------------------------------------------------------------------

/**
 * Enables the ternary operator: <boolean expr> .. <if-expr> ?: <else-expr>
 */
@Suppress("NOTHING_TO_INLINE")
operator inline fun <T: Any> Boolean.rangeTo(e: T): T?
    = if (this) e else null

// -------------------------------------------------------------------------------------------------

/**
 * Returns a string representation of the stack trace.
 */
fun Throwable.stackTraceString(): String {
    val sw = StringWriter()
    printStackTrace(PrintWriter(sw))
    return sw.toString()
}

// -------------------------------------------------------------------------------------------------

/**
 * Casts [list] to mutable if it can be, otherwise make a copy.
 */
fun <T> mutable (list: List<T>): MutableList<T>
{
    return if (list is MutableList)
        list
    else
        list.toMutableList()
}
// -------------------------------------------------------------------------------------------------

/**
 * Shorthand for [StringBuilder.append].
 */
operator fun StringBuilder.plusAssign(o: Any?) { append(o) }

// -------------------------------------------------------------------------------------------------

/**
 * Casts the receiver to [T].
 *
 * This is more useful than regular casts because it enables casts to non-denotable types
 * through type inference.
 */
@Suppress("UNCHECKED_CAST")
fun <T> Any?.cast() = this as T

// -------------------------------------------------------------------------------------------------

/**
 * Returns the result of [f].
 * The point is to allow statements in an expression context.
*/
inline fun <T> expr(f: () -> T): T = f()

// -------------------------------------------------------------------------------------------------

/**
 * Returns the receiver after evaluating [f] on it.
 */
inline infix fun <T> T.after(f: (T) -> Unit): T {
    f(this)
    return this
}
// -------------------------------------------------------------------------------------------------

/**
 * Reads a complete file and returns its contents as a string.
 * @throws IOException see [Files.readAllBytes]
 * @throws InvalidPathException see [Paths.get]
 */
fun readFile(file: String)
    = String(Files.readAllBytes(Paths.get(file)))

// -------------------------------------------------------------------------------------------------

/**
 * Like `String.substring` but allows [start] and [end] to be negative numbers.
 *
 * The first item in the sequence has index `0` (same as `-length`).
 * The last item in the sequence has index `length-1` (same as `-1`).
 *
 * The [start] bound is always inclusive.
 * The [end] bound is exclusive if positive, else inclusive.
 *
 * Throws an [IndexOutOfBoundsException] if the [start] bounds refers to an index below `0` or
 * if [end] refers to an index equal to or past `CharSequence.length`.
 *
 * It is fine to call this with [start] referring to `a` and [end] referring to `b` such that
 * `a > b`, as long the previous condition is respected. The result is then the empty string.
 */
operator fun CharSequence.get(start: Int, end: Int = length): String {
    val a = if (start >= 0) start else length + start
    val b = if (end >= 0) end else length + end + 1
    if (a < 0) throw IndexOutOfBoundsException("Start index < 0")
    if (b > length) throw IndexOutOfBoundsException("End index > length")
    if (a > b) return ""
    return substring(a, b)
}

// -------------------------------------------------------------------------------------------------

