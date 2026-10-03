package android.os

/** JVM stand-in: report a modern SDK level so the core enables TLS 1.3. */
object Build {
    object VERSION { const val SDK_INT = 34 }
    object VERSION_CODES { const val Q = 29 }
}
