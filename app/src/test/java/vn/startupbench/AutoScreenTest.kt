package vn.startupbench

import org.junit.Assert.*
import org.junit.Test

class AutoScreenTest {
    private fun content() = listOf(ScreenElement("TextView", "pkg:id/title", "Home", false),
        ScreenElement("Button", "pkg:id/create", "Create", true), ScreenElement("Button", "pkg:id/history", "History", true))
    @Test fun selectsContentWithoutUserSuppliedMarkerAndOrderDoesNotChangeSignature() {
        val a = AutoScreen.evaluate(content(), true, false)
        assertTrue(a.eligible); assertEquals(64, a.signature.length)
        assertEquals(a.signature, AutoScreen.evaluate(content().reversed(), true, false).signature)
        assertNotEquals(a.signature, AutoScreen.evaluate(content().dropLast(1) + ScreenElement("Button", "other", "Settings", true), true, false).signature)
    }
    @Test fun refusesAdsUnreadableTreesSplashAndLoadingEvenWhenOtherContentIsPresent() {
        assertEquals("ads_visible", AutoScreen.evaluate(content(), true, true).reason)
        assertEquals("ui_unreadable", AutoScreen.evaluate(content(), false, false).reason)
        assertEquals("loading_visible", AutoScreen.evaluate(content() + ScreenElement("ProgressBar", "", "", false), true, false).reason)
        assertEquals("loading_visible", AutoScreen.evaluate(content() + ScreenElement("ImageView", "pkg:id/splash_logo", "", false), true, false).reason)
        assertEquals("loading_visible", AutoScreen.evaluate(content() + ScreenElement("TextView", "", "Đang tải…", false), true, false).reason)
    }
    @Test fun logoOnlyOrNoninteractiveContentCannotBecomeAutomaticResult() {
        assertEquals("insufficient_content", AutoScreen.evaluate(listOf(ScreenElement("ImageView", "logo", "App", false)), true, false).reason)
        assertFalse(AutoScreen.evaluate(content().map { it.copy(actionable = false) }, true, false).eligible)
    }
    @Test fun screenTransitionResetsTheObservationInsteadOfCountingPreviousSplashTime() {
        val marker = MarkerStability()
        marker.reset(1000); marker.observe(true, 2000, 1500)
        marker.reset(2500) // signature changes
        marker.observe(true, 2500, 1500); marker.observe(true, 3900, 1500)
        assertEquals(-1L, marker.stable)
        marker.observe(true, 4000, 1500); assertEquals(2500L, marker.first)
    }
}
