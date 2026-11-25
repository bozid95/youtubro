// PoTokenException.kt — ported from Mochy Music (GPL-3.0)

package pro.iyou.data.stream.potoken

class PoTokenException(message: String) : Exception(message)

class BadWebViewException(message: String) : Exception(message)

fun buildExceptionForJsError(error: String): Exception {
    return if (error.contains("SyntaxError"))
        BadWebViewException(error)
    else
        PoTokenException(error)
}
