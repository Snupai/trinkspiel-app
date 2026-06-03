package com.snupai.trinkspiel.sync

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HttpRemoteCardSyncReferenceBackendTest {
    @Test
    fun appHttpClientPushesAndFetchesThroughReferenceBackend() = runBlocking {
        assumeTrue("Node.js is required for the local card-sync reference backend", hasNodeRuntime())
        val repoRoot = findRepoRoot()
        val ownerId = cardUserIdForName("WG Bibliothek")
        val dataDir = Files.createTempDirectory("seemops-card-sync-e2e").toFile()
        val process = ProcessBuilder(
            "node",
            File(repoRoot, "scripts/card-sync-server.mjs").absolutePath,
            "--port",
            "0",
            "--data-dir",
            dataDir.absolutePath,
            "--tokens",
            "writer:$ownerId:write:account_mika:Mika%20Server," +
                "admin:$ownerId:admin:account_admin:Admin," +
                "sam:$ownerId:write:account_sam:Sam," +
                "reader:$ownerId:read",
        )
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()

        try {
            val baseUrl = waitForReadyBaseUrl(process)
            val api = HttpRemoteCardSyncApi()
            val created = api.pushCards(
                config = RemoteSyncConfig(baseUrl, "writer"),
                ownerUserId = ownerId,
                requests = listOf(
                    RemoteCardPushRequest(
                        clientLocalId = 77,
                        operation = RemoteCardPushOperation.CREATE,
                        entry = DrinkEntry(
                            id = 77,
                            text = "Token Beitrag",
                            drinks = 4,
                            category = CardCategory.SPICY.id,
                            packName = "WG",
                            isEnabled = false,
                            isPendingReview = true,
                            questionLevel = QuestionLevel.LEVEL_3.id,
                            ownerUserId = ownerId,
                            ownerName = "WG Bibliothek",
                            contributorUserId = "malicious_contributor",
                            contributorName = "Fake Mika",
                            updatedAtMillis = 1,
                        ),
                    )
                ),
            )

            assertEquals(1, created.size)
            val createdCard = created.first().card
            assertEquals(77L, created.first().clientLocalId)
            assertTrue(createdCard.remoteId.startsWith("server-"))
            assertEquals(ownerId, createdCard.ownerUserId)
            assertEquals("account_mika", createdCard.contributorUserId)
            assertEquals("Mika Server", createdCard.contributorName)
            assertEquals(QuestionLevel.LEVEL_3.id, createdCard.questionLevel)
            assertTrue(createdCard.isPendingReview)

            val fetched = api.fetchCards(
                config = RemoteSyncConfig(baseUrl, "reader"),
                ownerUserId = ownerId,
            )

            assertEquals(1, fetched.size)
            assertEquals(createdCard.remoteId, fetched.first().remoteId)
            assertEquals("Token Beitrag", fetched.first().text)
            assertEquals("account_mika", fetched.first().contributorUserId)
            assertEquals("Mika Server", fetched.first().contributorName)

            val invite = api.createBackendInvite(
                config = RemoteSyncConfig(baseUrl, "admin"),
                ownerUserId = ownerId,
                ownerName = "WG Bibliothek",
                contributorName = "Nora",
                role = "write",
            )

            assertEquals(baseUrl, invite.endpointUrl)
            assertEquals(ownerId, invite.libraryOwnerUserId)
            assertEquals("WG Bibliothek", invite.libraryOwnerName)
            assertTrue(invite.accessToken.startsWith("invite_"))
            assertTrue(invite.contributorUserId.startsWith("account_nora_"))
            assertEquals("Nora", invite.contributorName)
            assertEquals("write", invite.role)

            val memberships = api.fetchLibraryMemberships(
                config = RemoteSyncConfig(baseUrl, "admin"),
                ownerUserId = ownerId,
            )
            val generatedMembership = memberships.first { membership ->
                membership.role == "write" &&
                    membership.contributorUserId == invite.contributorUserId &&
                    membership.source == "generated"
            }

            assertTrue(
                memberships.any { membership ->
                    membership.role == "admin" &&
                        membership.contributorUserId == "account_admin" &&
                        membership.source == "configured"
                }
            )
            assertTrue(
                memberships.any { membership ->
                    membership.role == "write" &&
                        membership.contributorUserId == invite.contributorUserId &&
                        membership.contributorName == "Nora" &&
                        membership.source == "generated"
                }
            )

            val invitedCreated = api.pushCards(
                config = RemoteSyncConfig(baseUrl, invite.accessToken),
                ownerUserId = ownerId,
                requests = listOf(
                    RemoteCardPushRequest(
                        clientLocalId = 79,
                        operation = RemoteCardPushOperation.CREATE,
                        entry = DrinkEntry(
                            id = 79,
                            text = "Nora Beitrag",
                            drinks = 2,
                            category = CardCategory.CHALLENGE.id,
                            packName = "WG",
                            questionLevel = QuestionLevel.LEVEL_2.id,
                            ownerUserId = ownerId,
                            ownerName = "WG Bibliothek",
                            contributorUserId = "spoofed",
                            contributorName = "Fake Nora",
                            updatedAtMillis = 1,
                        ),
                    )
                ),
            )

            assertEquals(1, invitedCreated.size)
            assertEquals(invite.contributorUserId, invitedCreated.first().card.contributorUserId)
            assertEquals("Nora", invitedCreated.first().card.contributorName)
            assertFalse(invitedCreated.first().card.isEnabled)
            assertTrue(invitedCreated.first().card.isPendingReview)

            val adminApprovedInviteCard = api.pushCards(
                config = RemoteSyncConfig(baseUrl, "admin"),
                ownerUserId = ownerId,
                requests = listOf(
                    RemoteCardPushRequest(
                        clientLocalId = 79,
                        operation = RemoteCardPushOperation.UPDATE,
                        entry = invitedCreated.first().card.toLocalEntry(79).copy(
                            isEnabled = true,
                            isPendingReview = false,
                        ),
                    )
                ),
            )

            assertEquals(1, adminApprovedInviteCard.size)
            assertTrue(adminApprovedInviteCard.first().card.isEnabled)
            assertFalse(adminApprovedInviteCard.first().card.isPendingReview)

            val deleteResult = api.deleteCards(
                config = RemoteSyncConfig(baseUrl, "admin"),
                ownerUserId = ownerId,
                remoteIds = listOf(
                    adminApprovedInviteCard.first().card.remoteId,
                    "missing-remote-card",
                ),
            )

            assertEquals(listOf(adminApprovedInviteCard.first().card.remoteId), deleteResult.deletedRemoteIds)
            assertEquals(listOf("missing-remote-card"), deleteResult.skippedRemoteIds)
            assertTrue(
                api.fetchCards(
                    config = RemoteSyncConfig(baseUrl, "reader"),
                    ownerUserId = ownerId,
                ).none { it.remoteId == adminApprovedInviteCard.first().card.remoteId }
            )

            assertTrue(
                api.revokeLibraryMembership(
                    config = RemoteSyncConfig(baseUrl, "admin"),
                    ownerUserId = ownerId,
                    tokenId = generatedMembership.tokenId,
                )
            )
            val revokedWrite = runCatching {
                api.pushCards(
                    config = RemoteSyncConfig(baseUrl, invite.accessToken),
                    ownerUserId = ownerId,
                    requests = listOf(
                        RemoteCardPushRequest(
                            clientLocalId = 80,
                            operation = RemoteCardPushOperation.CREATE,
                            entry = DrinkEntry(
                                id = 80,
                                text = "Nora nach Widerruf",
                                drinks = 2,
                                category = CardCategory.CHALLENGE.id,
                                packName = "WG",
                                questionLevel = QuestionLevel.LEVEL_2.id,
                                ownerUserId = ownerId,
                                ownerName = "WG Bibliothek",
                                contributorUserId = invite.contributorUserId,
                                contributorName = "Nora",
                                updatedAtMillis = 2,
                            ),
                        )
                    ),
                )
            }
            assertTrue(revokedWrite.isFailure)
            assertTrue(revokedWrite.exceptionOrNull()?.message.orEmpty().contains("401"))

            val blockedUpdate = runCatching {
                api.pushCards(
                    config = RemoteSyncConfig(baseUrl, "sam"),
                    ownerUserId = ownerId,
                    requests = listOf(
                        RemoteCardPushRequest(
                            clientLocalId = 78,
                            operation = RemoteCardPushOperation.UPDATE,
                            entry = DrinkEntry(
                                id = 78,
                                remoteId = createdCard.remoteId,
                                text = "Sam überschreibt Mika",
                                drinks = 2,
                                category = CardCategory.CHALLENGE.id,
                                packName = "WG",
                                questionLevel = QuestionLevel.LEVEL_2.id,
                                ownerUserId = ownerId,
                                ownerName = "WG Bibliothek",
                                contributorUserId = "account_sam",
                                contributorName = "Sam",
                                updatedAtMillis = createdCard.updatedAtMillis + 1,
                            ),
                        )
                    ),
                )
            }

            assertTrue(blockedUpdate.isFailure)
            assertTrue(blockedUpdate.exceptionOrNull()?.message.orEmpty().contains("403"))
        } finally {
            stopProcess(process)
            dataDir.deleteRecursively()
        }
    }
}

private fun hasNodeRuntime(): Boolean =
    try {
        val process = ProcessBuilder("node", "--version")
            .redirectErrorStream(true)
            .start()
        process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }

private fun findRepoRoot(): File {
    val userDir = System.getProperty("user.dir") ?: "."
    var current: File? = File(userDir).absoluteFile
    while (current != null) {
        if (File(current, "scripts/card-sync-server.mjs").isFile) return current
        current = current.parentFile
    }
    error("Could not find scripts/card-sync-server.mjs from $userDir")
}

private fun waitForReadyBaseUrl(process: Process): String {
    val executor = Executors.newSingleThreadExecutor()
    val future = executor.submit(Callable<String> {
        val output = StringBuilder()
        val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
        var baseUrl: String? = null
        while (baseUrl == null) {
            val line = reader.readLine()
                ?: error("Reference backend exited before ready output. Output: $output")
            output.appendLine(line)
            if (line.trim().startsWith("{")) {
                baseUrl = JSONObject(line).getString("baseUrl")
            }
        }
        baseUrl
    })
    return try {
        future.get(5, TimeUnit.SECONDS)
    } finally {
        executor.shutdownNow()
    }
}

private fun stopProcess(process: Process) {
    process.destroy()
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(2, TimeUnit.SECONDS)
    }
}
