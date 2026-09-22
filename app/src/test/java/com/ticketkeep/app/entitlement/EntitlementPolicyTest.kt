package com.ticketkeep.app.entitlement

import com.ticketkeep.app.data.repository.TicketRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权益矩阵：本地买断与云订阅独立；合并函数在查询失败时不误清缓存。
 */
class EntitlementPolicyTest {

    private fun snap(
        local: LocalUnlockStatus = LocalUnlockStatus.NOT_OWNED,
        cloud: CloudSubStatus = CloudSubStatus.EXPIRED,
        legacy: Boolean = false,
    ) = EntitlementSnapshot(
        localStatus = local,
        cloudStatus = cloud,
        legacyYearlyActive = legacy,
    )

    @Test
    fun none_limitedTickets_noExport_noCloud() {
        val s = snap()
        assertFalse(s.hasLocalPremium)
        assertFalse(s.canAddUnlimited)
        assertFalse(s.canExportPdf)
        assertFalse(s.canImportExportCsv)
        assertFalse(s.canUploadCloud)
        assertFalse(s.canRestoreCloud)
        assertFalse(TicketRepository.canAdd(hasLocalPremium = s.hasLocalPremium, count = 10))
    }

    @Test
    fun localOnly_unlimitedAndExport_noCloud() {
        val s = snap(local = LocalUnlockStatus.OWNED)
        assertTrue(s.hasLocalPremium)
        assertTrue(s.canExportPdf)
        assertTrue(s.canImportExportCsv)
        assertFalse(s.canUploadCloud)
        assertTrue(TicketRepository.canAdd(hasLocalPremium = s.hasLocalPremium, count = 100))
    }

    @Test
    fun cloudOnly_limitedTickets_cloudYes_noExport() {
        val s = snap(cloud = CloudSubStatus.ACTIVE)
        assertFalse(s.hasLocalPremium)
        assertFalse(s.canExportPdf)
        assertTrue(s.canUploadCloud)
        assertTrue(s.canRestoreCloud)
        assertFalse(TicketRepository.canAdd(hasLocalPremium = s.hasLocalPremium, count = 10))
    }

    @Test
    fun both_allCapabilities() {
        val s = snap(local = LocalUnlockStatus.OWNED, cloud = CloudSubStatus.ACTIVE)
        assertTrue(s.hasLocalPremium)
        assertTrue(s.canExportPdf)
        assertTrue(s.canUploadCloud)
        assertTrue(s.canRestoreCloud)
    }

    @Test
    fun cloudExpired_localOwned_localYes_cloudNo() {
        val s = snap(local = LocalUnlockStatus.OWNED, cloud = CloudSubStatus.EXPIRED)
        assertTrue(s.hasLocalPremium)
        assertTrue(s.canExportPdf)
        assertFalse(s.canUploadCloud)
        assertFalse(s.canRestoreCloud)
    }

    @Test
    fun legacyYearlyActive_grantsLocal_notCloud() {
        val s = snap(legacy = true)
        assertTrue(s.hasLocalPremium)
        assertTrue(s.canAddUnlimited)
        assertFalse(s.canUploadCloud)
        // 若同时有云订阅才有云
        val both = snap(legacy = true, cloud = CloudSubStatus.ACTIVE)
        assertTrue(both.canUploadCloud)
    }

    @Test
    fun canceledButActive_andGrace_allowCloud() {
        assertTrue(snap(cloud = CloudSubStatus.CANCELED_BUT_ACTIVE).canUploadCloud)
        assertTrue(snap(cloud = CloudSubStatus.IN_GRACE).canUploadCloud)
        assertFalse(snap(cloud = CloudSubStatus.ON_HOLD).canUploadCloud)
        assertFalse(snap(cloud = CloudSubStatus.REVOKED).canUploadCloud)
    }

    @Test
    fun mergeLocal_queryFailed_keepsOwnedCache() {
        val merged = EntitlementMerger.mergeLocalAfterBillingQuery(
            cached = LocalUnlockStatus.OWNED,
            querySucceeded = false,
            owned = false,
        )
        assertEquals(LocalUnlockStatus.OWNED, merged)
    }

    @Test
    fun mergeLocal_unknownPlusOwnedCache_notWipedOnFailedBilling() {
        val merged = EntitlementMerger.mergeLocalAfterBillingQuery(
            cached = LocalUnlockStatus.UNKNOWN,
            querySucceeded = false,
            owned = false,
        )
        assertEquals(LocalUnlockStatus.UNKNOWN, merged)
    }

    @Test
    fun mergeLocal_queryOk_notOwned() {
        val merged = EntitlementMerger.mergeLocalAfterBillingQuery(
            cached = LocalUnlockStatus.OWNED,
            querySucceeded = true,
            owned = false,
        )
        assertEquals(LocalUnlockStatus.NOT_OWNED, merged)
    }

    @Test
    fun mergeCloud_emptySuccess_expires_doesNotAffectLocalSemantics() {
        val cloud = EntitlementMerger.mergeCloudAfterBillingQuery(
            cached = CloudSubStatus.ACTIVE,
            querySucceeded = true,
            activeLike = false,
        )
        assertEquals(CloudSubStatus.EXPIRED, cloud)
        // 本地快照独立
        val s = snap(local = LocalUnlockStatus.OWNED, cloud = cloud)
        assertTrue(s.hasLocalPremium)
        assertFalse(s.canUploadCloud)
    }

    @Test
    fun mergeCloud_queryFailed_keepsCache() {
        val merged = EntitlementMerger.mergeCloudAfterBillingQuery(
            cached = CloudSubStatus.ACTIVE,
            querySucceeded = false,
            activeLike = false,
        )
        assertEquals(CloudSubStatus.ACTIVE, merged)
    }
}
