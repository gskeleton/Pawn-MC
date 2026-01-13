package com.rvdjv.pawnmc

import android.os.Handler
import android.os.Looper

/**
 * Kotlin wrapper for the Pawn Compiler native library.
 * Provides thread-safe compilation with callback support.
 */
object PawnCompiler {

    init {
        System.loadLibrary("pawnc_jni")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var outputListener: ((String) -> Unit)? = null
    private var errorListener: ((CompileError) -> Unit)? = null
    private val errorsList = mutableListOf<CompileError>()

    // jni cbi
    private val outputCallback = object : OutputCallback {
        override fun onOutput(message: String) {
            mainHandler.post {
                outputListener?.invoke(message)
            }
        }
    }

    private val errorCallback = object : ErrorCallback {
        override fun onError(number: Int, filename: String, firstline: Int, lastline: Int, message: String) {
            val error = CompileError(number, filename, firstline, lastline, message)
            synchronized(errorsList) {
                errorsList.add(error)
            }
            mainHandler.post {
                errorListener?.invoke(error)
            }
        }
    }

    /**
     * set output listener for compiler output messages.
     * callbacks are delivered on the main thread.
     */
    fun setOutputListener(listener: ((String) -> Unit)?) {
        outputListener = listener
        if (listener != null) {
            nativeSetOutputCallback(outputCallback)
        } else {
            nativeSetOutputCallback(null)
        }
    }

    /**
     * set error listener for compiler errors and warnings.
     * callbacks are delivered on the main thread.
     */
    fun setErrorListener(listener: ((CompileError) -> Unit)?) {
        errorListener = listener
        if (listener != null) {
            nativeSetErrorCallback(errorCallback)
        } else {
            nativeSetErrorCallback(null)
        }
    }

    /**
     * compile a pawn source file.
     * this method should be called from a background thread.
     *
     * @param sourceFile absolute path to the .pwn source file
     * @param options additional compiler options (e.g., "-i/path/to/includes")
     * @return CompileResult with success status and list of errors/warnings
     */
    fun compile(sourceFile: String, options: List<String> = emptyList()): CompileResult {
        synchronized(errorsList) {
            errorsList.clear()
        }

        // Build argument list: pawncc <options> <sourcefile>
        val args = mutableListOf("pawncc")
        args.addAll(options)
        args.add(sourceFile)

        val result = nativeCompile(args.toTypedArray())

        val errors: List<CompileError>
        synchronized(errorsList) {
            errors = errorsList.toList()
        }

        return CompileResult(
            success = result == 0,
            errors = errors
        )
    }

    /**
     * clear all callbacks and release resources.
     */
    fun clearCallbacks() {
        outputListener = null
        errorListener = null
        nativeClearCallbacks()
    }

    private external fun nativeCompile(args: Array<String>): Int
    private external fun nativeSetOutputCallback(callback: OutputCallback?)
    private external fun nativeSetErrorCallback(callback: ErrorCallback?)
    private external fun nativeClearCallbacks()

    interface OutputCallback {
        fun onOutput(message: String)
    }

    interface ErrorCallback {
        fun onError(number: Int, filename: String, firstline: Int, lastline: Int, message: String)
    }
}
