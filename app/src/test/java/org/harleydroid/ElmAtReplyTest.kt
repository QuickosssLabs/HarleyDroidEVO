package org.harleydroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmAtReplyTest {
    @Test
    fun statusLinesAreRecognized() {
        assertTrue(ElmAtReply.isStatusLine("OK"))
        assertTrue(ElmAtReply.isStatusLine("SEARCHING..."))
        assertTrue(ElmAtReply.isStatusLine("BUFFER FULL"))
        assertTrue(ElmAtReply.isStatusLine("STOPPED"))
        assertTrue(ElmAtReply.isStatusLine("ATMA"))
        assertTrue(ElmAtReply.isStatusLine("ELM327 v1.5"))
        assertFalse(ElmAtReply.isStatusLine("28 1B 10 02 0F A0 D7"))
    }

    @Test
    fun bufferFullNeedsRestart() {
        assertTrue(ElmAtReply.needsAtmaRestart("BUFFER FULL"))
        assertTrue(ElmAtReply.needsAtmaRestart("STOPPED"))
        assertFalse(ElmAtReply.needsAtmaRestart("28 1B 10 02"))
    }
}
