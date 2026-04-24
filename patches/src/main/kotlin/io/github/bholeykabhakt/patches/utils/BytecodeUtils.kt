package io.github.bholeykabhakt.patches.utils

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod


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
