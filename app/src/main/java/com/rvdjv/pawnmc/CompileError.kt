package com.rvdjv.pawnmc

/**
 * for compilation results of warnings or errors
 * @param number error number (0=info, 1-99=error, 100-199=fatal, 200+=warning)
 * @param file source file path
 * @param firstLine first line number (-1 if N/A)
 * @param lastLine last line number
 * @param message error message
 */
data class CompileError(
    val number: Int,
    val file: String,
    val firstLine: Int,
    val lastLine: Int,
    val message: String
) {
    val isWarning: Boolean get() = number >= 200
    val isFatal: Boolean get() = number in 100..199
    val isError: Boolean get() = number in 1..99

    val lineInfo: String
        get() = when {
            firstLine >= 0 -> "($firstLine-$lastLine)"
            lastLine >= 0 -> "($lastLine)"
            else -> ""
        }
}
