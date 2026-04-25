package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.Method

private const val matchLoggingEnvVar = "MORPHE_PRINT_MATCHES"
private const val matchLoggingProperty = "morphe.printMatches"

private fun Method.fingerprintSignature() =
    "$definingClass->$name(${parameterTypes.joinToString(separator = "")})$returnType"

private fun String?.isEnabledFlag() = when (this?.trim()?.lowercase()) {
    "1", "true", "yes", "on" -> true
    else -> false
}

private fun isMatchLoggingEnabled() =
    System.getenv(matchLoggingEnvVar).isEnabledFlag() ||
            System.getProperty(matchLoggingProperty).isEnabledFlag()

private fun Fingerprint.displayName() = javaClass.simpleName.ifBlank { toString() }

context(_: BytecodePatchContext)
val Fingerprint.logMatch: Match
    get() {
    val label = displayName()
    val matches = matchAllOrNull() ?: throw patchException()

    if (isMatchLoggingEnabled()) {
        matches.forEachIndexed { index, match ->
            println("MATCH: $label matched[$index]: ${match.originalMethod.fingerprintSignature()}")
        }
    }

    if (matches.size != 1) {
        throw PatchException("$label expected exactly 1 match but found ${matches.size}")
    }

    return matches.single()
    }


fun MutableMethod.returnEarly(retVal: String = "0x0") {
    val stringInstructions = when (this.returnType) {

        "Ljava/lang/String;" ->
            """
                const-string v0, "$retVal"
                return-object v0
            """

        "J" ->
            """
                const-wide/16 v0, $retVal
                return-wide v0
            """

        "V" -> "return-void"

        "I", "Z" -> {
            val intValue = retVal.toIntOrNull(16) ?: 0 // Convert hex string to int, default to 0
            if (intValue in -8..7) {
                """
                    const/4 v0, $intValue
                    return v0
                """
            } else {
                """
                    const v0, $intValue
                    return v0
                """
            }
        }

        else -> throw IllegalStateException("Unexpected return type: ${this.returnType}")
    }

    this.addInstructions(0, stringInstructions)
}
