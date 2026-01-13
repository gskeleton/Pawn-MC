package com.rvdjv.pawnmc

/**
 * result of a Pawn compilation.
 * @param success true if compilation succeeded (exit code 0)
 * @param errors list of errors and warnings encountered during compilation
 */
data class CompileResult(
    val success: Boolean,
    val errors: List<CompileError>
)
