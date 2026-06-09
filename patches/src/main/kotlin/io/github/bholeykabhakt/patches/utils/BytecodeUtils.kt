package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.Method

private const val MATCH_LOGGING_ENV_VAR = "MORPHE_PRINT_MATCHES"
private const val MATCH_LOGGING_PROPERTY = "morphe.printMatches"

private fun Method.fingerprintSignature() =
    "$definingClass->$name(${parameterTypes.joinToString(separator = "")})$returnType"

private fun String?.isEnabledFlag() = when (this?.trim()?.lowercase()) {
    "1", "true", "yes", "on" -> true
    else -> false
}

private fun isMatchLoggingEnabled() = System.getenv(MATCH_LOGGING_ENV_VAR).isEnabledFlag() ||
        System.getProperty(MATCH_LOGGING_PROPERTY).isEnabledFlag()

private fun Fingerprint.displayName() = javaClass.simpleName.ifBlank { toString() }

context(_: BytecodePatchContext)
private fun Fingerprint.matchesAndLog(): List<Match> {
    val matches = matchAllOrNull() ?: throw patchException()
    if (isMatchLoggingEnabled()) {
        val label = displayName()
        matches.forEachIndexed { i, match ->
            println("MATCH: $label matched[$i]: ${match.originalMethod.fingerprintSignature()}")
        }
    }
    return matches
}

/** The single match. Throws if 0 or 2+ matched (catches under-specified fingerprints). */
context(_: BytecodePatchContext)
val Fingerprint.logMatch: Match
    get() {
        val matches = matchesAndLog()
        if (matches.size != 1) {
            throw PatchException("${displayName()} expected exactly 1 match but found ${matches.size}")
        }
        return matches.single()
    }

/** All matches (1 or more). Throws if 0 matched. Use when one fingerprint legitimately covers several methods. */
context(_: BytecodePatchContext)
val Fingerprint.logMatchAll: List<Match>
    get() = matchesAndLog()

/**
 * Insert an early return at method entry, returning the natural zero value for the
 * method's return type:
 *
 * | Return type        | Emitted             |
 * |--------------------|---------------------|
 * | `V`                | `return-void`       |
 * | `Z B C S I F`      | `return 0`          |
 * | `J D`              | `return-wide 0`     |
 * | object / array     | `return-object null`|
 *
 * Use the typed overloads to return a non-zero value.
 */
fun MutableMethod.returnEarly() = addInstructions(
    0,
    when (returnType) {
        "V" -> "return-void"
        "Z", "B", "C", "S", "I", "F" -> "const/4 v0, 0x0\nreturn v0"
        "J", "D" -> "const-wide/16 v0, 0x0\nreturn-wide v0"
        else -> "const/4 v0, 0x0\nreturn-object v0"
    },
)

/** Return a fixed boolean. Method must return `Z`. */
fun MutableMethod.returnEarly(value: Boolean) {
    require(returnType == "Z") { "returnEarly(Boolean) needs Z return, got $returnType" }
    addInstructions(0, "const/4 v0, 0x${if (value) 1 else 0}\nreturn v0")
}

/** Return a fixed int. Method must return `Z`, `B`, `C`, `S`, or `I`. */
fun MutableMethod.returnEarly(value: Int) {
    require(returnType in setOf("Z", "B", "C", "S", "I")) {
        "returnEarly(Int) needs Z/B/C/S/I return, got $returnType"
    }
    val literal = when (value) {
        in -8..7 -> "const/4 v0, $value"
        in Short.MIN_VALUE..Short.MAX_VALUE -> "const/16 v0, $value"
        else -> "const v0, $value"
    }
    addInstructions(0, "$literal\nreturn v0")
}

/** Return a fixed long. Method must return `J`. */
fun MutableMethod.returnEarly(value: Long) {
    require(returnType == "J") { "returnEarly(Long) needs J return, got $returnType" }
    val literal = when (value) {
        in Short.MIN_VALUE..Short.MAX_VALUE -> "const-wide/16 v0, $value"
        in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() -> "const-wide/32 v0, $value"
        else -> "const-wide v0, ${value}L"
    }
    addInstructions(0, "$literal\nreturn-wide v0")
}

/** Return a fixed String literal. Method must return `Ljava/lang/String;`. */
fun MutableMethod.returnEarly(value: String) {
    require(returnType == "Ljava/lang/String;") {
        "returnEarly(String) needs Ljava/lang/String; return, got $returnType"
    }
    addInstructions(0, "const-string v0, \"$value\"\nreturn-object v0")
}
