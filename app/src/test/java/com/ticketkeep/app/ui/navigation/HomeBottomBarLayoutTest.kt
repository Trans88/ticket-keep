package com.ticketkeep.app.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBottomBarLayoutTest {
    @Test fun measuredHeightIncludesInsetsOnlyOnce() {
        assertEquals(122.dp, calculateHomeBottomBarListBottomPadding(24.dp, 72.dp))
        assertEquals(146.dp, calculateHomeBottomBarListBottomPadding(48.dp, 72.dp))
    }

    @Test fun largeFontHeightIsReserved() {
        assertEquals(158.dp, calculateHomeBottomBarListBottomPadding(24.dp, 108.dp))
    }

    @Test fun initialMeasurementIsSafe() {
        assertEquals(98.dp, calculateHomeBottomBarListBottomPadding(0.dp, 0.dp))
    }

    @Test fun dockOnlyAppearsOnTopLevelDestinations() {
        assertTrue(shouldShowHomeBottomBar(Routes.LIST))
        assertTrue(shouldShowHomeBottomBar(Routes.SETTINGS))
        assertFalse(shouldShowHomeBottomBar(Routes.PAYWALL))
        assertFalse(shouldShowHomeBottomBar(Routes.EDIT))
        assertFalse(shouldShowHomeBottomBar(null))
    }
}
