package android.util

/** JVM stand-in for android.util.Log so the shared core compiles unchanged. */
object Log {
    fun d(tag: String, msg: String, tr: Throwable? = null): Int = out("D", tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null): Int = out("I", tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null): Int = out("W", tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null): Int = out("E", tag, msg, tr)

    private fun out(level: String, tag: String, msg: String, tr: Throwable?): Int {
        System.err.println("$level/$tag: $msg" + (tr?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
        return 0
    }
}
