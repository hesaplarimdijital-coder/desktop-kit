package desktopkit.components

import android.content.ClipDescription
import android.os.PersistableBundle
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What the kit's drags carry (K6): everything for the app's windows, only the label for other apps. */
@RunWith(AndroidJUnit4::class)
class DragAndDropTest {

    @Test
    fun otherAppsSeeOnlyTheLabel() {
        val payload = DeskDragPayload("track", listOf("t1", "t2", "t3"), "3 parça")
        val data = payload.toTransferData(acrossWindows = true)
        val description = data.clipData.description
        assertEquals("3 parça", description.label)
        assertTrue(description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
        assertEquals(1, data.clipData.itemCount)
        assertEquals("other apps get the label as text", "3 parça", data.clipData.getItemAt(0).text)
        // what every window can read names neither the kind nor a key
        val extras = description.extras
        val shown = extras.keySet().map { extras.getString(it).orEmpty() }
        assertTrue(shown.joinToString(), shown.none { "track" in it || "t1" in it })
        assertEquals(View.DRAG_FLAG_GLOBAL, data.flags)
        assertEquals(payload, data.localState)
    }

    @Test
    fun theAppsOtherWindowsGetAllOfIt() {
        val payload = DeskDragPayload("file", listOf("a.zip", "b.flac"), "2 dosya")
        val description = payload.toTransferData(acrossWindows = true).clipData.description
        // another window of the app: no local state, the same process
        assertEquals(payload, DeskDragPayload.from(null, description))
        // a drag from another app, even one built on the kit, is not taken
        val foreign = ClipDescription("x", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))
        foreign.extras = PersistableBundle().apply { putString("desktopkit.drag", "f0e1d2c3-0000-4000-8000-000000000000") }
        assertNull(DeskDragPayload.from(null, foreign))
        assertNull(DeskDragPayload.from(null, null))
    }

    @Test
    fun withinTheWindowOnly() {
        val data = DeskDragPayload("file", listOf("a.zip"), "a.zip").toTransferData(acrossWindows = false)
        assertEquals(0, data.flags)
    }
}
