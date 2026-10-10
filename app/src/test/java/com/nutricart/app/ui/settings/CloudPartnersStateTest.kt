package com.nutricart.app.ui.settings

import com.nutricart.app.cloud.CloudPartner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Who can see your day" after a load: a failed load is not "Nobody yet". */
class CloudPartnersStateTest {

    private val olena = CloudPartner("l1", "Olena", 1_790_000_000_000L)
    private val taras = CloudPartner("l2", "Taras", null)

    @Test
    fun `a loaded list replaces what was shown and clears an earlier failure`() {
        val state = SettingsUiState(cloudPartners = listOf(olena), cloudPartnersFailed = true)
            .withCloudPartners(listOf(olena, taras))
        assertEquals(listOf(olena, taras), state.cloudPartners)
        assertFalse(state.cloudPartnersFailed)
    }

    @Test
    fun `an empty list is a real answer, nobody yet`() {
        val state = SettingsUiState(cloudPartners = listOf(olena)).withCloudPartners(emptyList())
        assertTrue(state.cloudPartners.isEmpty())
        assertFalse(state.cloudPartnersFailed)
    }

    @Test
    fun `a failed load keeps the people shown, so Remove stays, and says it failed`() {
        val state = SettingsUiState(cloudPartners = listOf(olena)).withCloudPartners(null)
        assertEquals(listOf(olena), state.cloudPartners)
        assertTrue(state.cloudPartnersFailed)
    }

    @Test
    fun `a failed first load shows no one and says it failed`() {
        val state = SettingsUiState().withCloudPartners(null)
        assertTrue(state.cloudPartners.isEmpty())
        assertTrue(state.cloudPartnersFailed)
    }
}
