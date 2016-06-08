package norswap.autumn

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
            ++ col
        }
    }
    return buf.toString()
}
