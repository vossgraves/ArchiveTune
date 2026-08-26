/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePresentationTest {
    @Test
    fun `credential mask keeps only the last four characters visible`() {
        assertEquals("********ijkl", maskCredential("abcdefghijkl"))
    }

    @Test
    fun `credential mask does not reveal short credentials`() {
        assertEquals("****", maskCredential("abcd"))
        assertEquals("", maskCredential(""))
    }
}
