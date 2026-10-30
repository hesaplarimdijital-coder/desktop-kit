package desktopkit.components

import android.content.ClipDescription
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What the kit's drags carry (K6): the kind and keys for the app's windows, the label for other apps. */
@RunWith(AndroidJUnit4::class)
class DragAndDropTest {

    @Test
    fun payloadTravelsInTheClip() {
        val payload = DeskDragPayload("track", listOf("t1", "t2", "t3"), "3 parça")
        val data = payload.toTransferData(acrossWindows = true)
        val description = data.clipData.description
        assertEquals("3 parça", description.label)
        assertTrue(description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
        assertEquals("other apps get the label as text", "3 parça", data.clipData.getItemAt(0).text)
        assertEquals("track", description.extras.getString("desktopkit.drag.kind"))
        assertArrayEquals(arrayOf("t1", "t2", "t3"), description.extras.getStringArray("desktopkit.drag.keys"))
        assertEquals(View.DRAG_FLAG_GLOBAL, data.flags)
        assertEquals(payload, data.localState)
    }

    @Test
    fun withinTheWindowOnly() {
        val data = DeskDragPayload("file", listOf("a.zip"), "a.zip").toTransferData(acrossWindows = false)
        assertEquals(0, data.flags)
    }
}
