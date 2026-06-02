package com.snupai.trinkspiel.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalCopyTest {

    @Test
    fun privacyPolicyStatesOfflineNoAdsNoTrackingAndDataBoundaries() {
        val text = LegalCopy.privacyPolicyText.lowercase()

        assertTrue(text.contains("offline"))
        assertTrue(text.contains("keine werbung"))
        assertTrue(text.contains("keine analyse"))
        assertTrue(text.contains("keine kartentexte"))
        assertTrue(text.contains("keine spielernamen"))
        assertTrue(text.contains("backend-invite"))
        assertTrue(text.contains("backend-mitglieder"))
        assertTrue(text.contains("beitraeger-profilnamen"))
        assertTrue(text.contains("widerrufst"))
        assertTrue(text.contains("mitgliedschafts-id"))
        assertTrue(text.contains("remote-karten-id"))
        assertTrue(text.contains("sync-token"))
        assertTrue(text.contains("token-rolle"))
        assertTrue(text.contains("gesetzlichen trinkalter"))
        assertFalse(text.contains("google analytics"))
        assertFalse(text.contains("firebase analytics"))
    }
}
