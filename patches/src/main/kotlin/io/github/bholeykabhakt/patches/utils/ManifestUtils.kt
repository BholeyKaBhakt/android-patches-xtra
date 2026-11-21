package io.github.bholeykabhakt.patches.utils

import org.w3c.dom.Document
import org.w3c.dom.Element

/** The manifest's `<application>` element. */
fun Document.applicationElement() = getElementsByTagName("application").item(0) as Element

/** Every descendant element named [tag] (nested components included). */
fun Element.elements(tag: String): List<Element> =
    getElementsByTagName(tag).let { nodes -> List(nodes.length) { nodes.item(it) as Element } }

/** Appends `<meta-data android:name="[name]" android:value="[value]"/>`. */
fun Element.appendMetaData(name: String, value: String) {
    val element = ownerDocument.createElement("meta-data")
    element.setAttribute("android:name", name)
    element.setAttribute("android:value", value)
    appendChild(element)
}
