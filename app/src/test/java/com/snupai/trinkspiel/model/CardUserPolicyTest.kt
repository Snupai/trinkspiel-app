package com.snupai.trinkspiel.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardUserPolicyTest {
    @Test
    fun sameOwnerAndContributorDoesNotRequireReview() {
        assertFalse(shouldReviewContribution("Lena", "lena"))
        assertFalse(shouldReviewContribution("Lokal", ""))
    }

    @Test
    fun externalContributorRequiresReview() {
        assertTrue(shouldReviewContribution("Lena", "Mika"))
        assertTrue(shouldReviewContribution("WG Bibliothek", "Gast"))
    }

    @Test
    fun sameDisplayNameWithDifferentAccountIdsRequiresReview() {
        assertTrue(
            shouldReviewContribution(
                ownerUserId = "library_wg",
                ownerName = "WG",
                contributorUserId = "account_wg",
                contributorName = "WG",
            )
        )
    }

    @Test
    fun sameBackendAccountDoesNotRequireReview() {
        assertFalse(
            shouldReviewContribution(
                ownerUserId = "account_lena",
                ownerName = "Lena",
                contributorUserId = "account_lena",
                contributorName = "Lena Admin",
            )
        )
    }
}
