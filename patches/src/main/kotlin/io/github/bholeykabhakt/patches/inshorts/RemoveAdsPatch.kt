package io.github.bholeykabhakt.patches.inshorts

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import io.github.bholeykabhakt.patches.shared.Constants.COMPATIBILITY_INSHORTS
import io.github.bholeykabhakt.patches.utils.returnEarly

/**
 * Removes every Inshorts ad surface (stack, full-page, top, bottom-bar — all Google
 * native/DFP). Two layers, since the app pulls ads from several places:
 *
 *  - **Producers → null:** the three managers that hand an ad to the card deck /
 *    top bar each return their no-ad value (`null`), so nothing is ever rendered.
 *    Callers already null-check (these methods return null in the normal "no ad" path).
 *  - **Config → empty:** the server ad-slot lists return empty and the bottom-bar DFP
 *    returns null, so the app never even schedules a load (and any surface that reads
 *    the config directly gets nothing).
 *
 * Method names are R8-obfuscated; the producers are matched by their (stable) class +
 * return/parameter types, the config getters by their kept Gson getter names.
 */
@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove Ads",
) {
    compatibleWith(COMPATIBILITY_INSHORTS)

    execute {
        fun find(clazz: String, predicate: (MutableMethod) -> Boolean): MutableMethod =
            mutableClassDefBy(clazz).methods.firstOrNull(predicate)
                ?: throw PatchException("Inshorts Remove Ads: target method not found in $clazz")

        // 1) Ad producers → null.
        find("Lcom/nis/app/ads/StackAdsManager;") {
            it.returnType == "Lcom/nis/app/ads/models/StackAd;" &&
                    it.parameterTypes.map(CharSequence::toString) == listOf("Lcom/nis/app/models/cards/CardData;")
        }.returnEarly()
        find("Lcom/nis/app/ads/FullPageAdsManager;") {
            it.returnType == "Lcom/nis/app/ads/models/FullPageAd;" &&
                    it.parameterTypes.map(CharSequence::toString) ==
                    listOf("Lcom/nis/app/network/models/config/AdSlot;", "I")
        }.returnEarly()
        find("Lcom/nis/app/ads/TopAdsManager;") {
            it.returnType == "Lcom/nis/app/ads/models/TopAd;" &&
                    it.parameterTypes.map(CharSequence::toString) == listOf("Lcom/nis/app/models/NewsCardData;")
        }.returnEarly()

        // 2) Server ad config → nothing to load.
        val emptyList = "new-instance v0, Ljava/util/ArrayList;\n" +
                "invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V\n" +
                "return-object v0"
        val config = "Lcom/nis/app/network/models/config/ConfigModel;"
        find(config) { it.name == "getAdSlots" }.addInstructions(0, emptyList)
        find(config) { it.name == "getDfpAdSlots" }.addInstructions(0, emptyList)
        find(config) { it.name == "getBottomBarDfp" }.returnEarly()
    }
}
