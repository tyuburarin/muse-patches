package local.muse.patches.grokheader

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatchContext
import org.w3c.dom.Element

/** Add a slot only to the focal header, which has no GrokAnalysePostStub. */
internal fun ResourcePatchContext.addFocalHeaderSlot() {
    val android = "http://schemas.android.com/apk/res/android"
    val app = "http://schemas.android.com/apk/res-auto"
    val slotId = "@+id/muse_focal_header_buttons"
    val slotRef = "@id/muse_focal_header_buttons"
    document("res/layout/linear_layout_tweet_view.xml").use { timeline ->
        val nodes = timeline.getElementsByTagName("ViewStub")
        val grok = (0 until nodes.length).map { nodes.item(it) as Element }.single {
            it.getAttributeNS(android, "id").endsWith("/tweet_grok_analyse_post")
        }
        document("res/layout/focal_tweet_view.xml").use { focal ->
            val headers = focal.getElementsByTagName("com.twitter.ui.tweet.TweetHeaderView")
            val header = (0 until headers.length).map { headers.item(it) as Element }.single {
                it.getAttributeNS(android, "id").endsWith("/tweet_header")
            }
            val parent = header.parentNode
            val end = header.getAttributeNS(app, "layout_constraintEnd_toStartOf")
            if (end.isEmpty()) throw PatchException("Focal header end constraint missing")
            val children = parent.childNodes
            val neighbour = (0 until children.length).mapNotNull { children.item(it) as? Element }
                .single { it.getAttributeNS(android, "id").substringAfter('/') == end.substringAfter('/') }
            val headerRef = "@id/tweet_header"
            if (neighbour.getAttributeNS(app, "layout_constraintStart_toEndOf") != headerRef) {
                throw PatchException("Unexpected focal header chain")
            }
            val row = focal.createElement("LinearLayout").apply {
                setAttributeNS(android, "android:id", slotId)
                setAttributeNS(android, "android:orientation", "horizontal")
                setAttributeNS(android, "android:layout_width", "wrap_content")
                setAttributeNS(android, "android:layout_height", "wrap_content")
                setAttributeNS(android, "android:visibility", "gone")
                setAttributeNS(app, "app:layout_constraintStart_toEndOf", headerRef)
                setAttributeNS(app, "app:layout_constraintEnd_toStartOf", end)
                setAttributeNS(app, "app:layout_constraintTop_toTopOf", headerRef)
                setAttributeNS(app, "app:layout_constraintBottom_toBottomOf", headerRef)
            }
            val anchor = focal.createElement("ImageView").apply {
                setAttributeNS(android, "android:id", "@+id/muse_focal_header_anchor")
                setAttributeNS(android, "android:visibility", "gone")
                // Copy the timeline Grok slot's exact dimensions and margins.
                for (i in 0 until grok.attributes.length) {
                    val attr = grok.attributes.item(i)
                    if (attr.namespaceURI == android && attr.localName.startsWith("layout_") &&
                        attr.localName != "layout") {
                        setAttributeNS(android, "android:${attr.localName}", attr.nodeValue)
                    }
                }
            }
            row.appendChild(anchor)
            parent.insertBefore(row, header)
            header.setAttributeNS(app, "app:layout_constraintEnd_toStartOf", slotRef)
            neighbour.setAttributeNS(app, "app:layout_constraintStart_toEndOf", slotRef)
        }
    }
}
