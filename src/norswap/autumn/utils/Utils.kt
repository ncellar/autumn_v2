package norswap.autumn.utils
import kotlin.reflect.KClass

/**
 * Returns a version of this string where all tabs have been fully expanded, so that each tab
 * brings the column (counted from 0 starting at each newline) to a multiple of [tabSize].
 */
// Courtesy of http://stackoverflow.com/a/34933524/298664
fun String.expandTabs(tabSize: Int): String {
    val buf = StringBuilder()
    var col = 0
    for (c in this) when (c) {
        '\n' -> {
            buf.append(c)
            col = 0
        }
        '\t' -> {
            val spaces = tabSize - col % tabSize
            repeat(spaces) { buf.append(" ") }
            col += spaces
        }
        else -> {
            buf.append(c)
            ++col
    }   }
    return buf.toString()
}

/**
 * Indicates if the element indicates a call within the given class and method.
 */
fun StackTraceElement.isMethod(klass: Class<*>, method: String)
    =  klass.isAssignableFrom(Class.forName(this.className))
    && method == this.methodName

/**
 * A string that describes this stack trace element, in a format recognized by IntelliJ,
 * for which it generates *correct* clickable links (important when inlining is involved).
 */
fun StackTraceElement.clickableString()
    = "at " + this

/**
 * Return a string describing the source code location of the stack trace element.
 * Extracted from [StackTraceElement.toString].
 */
fun StackTraceElement.location(): String = run {
    if (fileName == null) "(Unknown Source)"
    else if (lineNumber >= 0) "($fileName:$lineNumber)"
    else "($fileName)"
}

/**
 * Indicates if the element indicates a call within the given class and method.
 */
fun StackTraceElement.isMethod(klass: KClass<*>, method: String)
    = isMethod(klass.java, method)

