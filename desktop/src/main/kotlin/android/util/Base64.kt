package android.util

/** JVM stand-in for android.util.Base64 (only the flags the core uses). */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    fun encodeToString(input: ByteArray, flags: Int): String {
        var enc = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) enc = enc.withoutPadding()
        return enc.encodeToString(input)
    }

    fun decode(input: String, flags: Int): ByteArray {
        val dec = if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder() else java.util.Base64.getMimeDecoder()
        return dec.decode(input)
    }
}
