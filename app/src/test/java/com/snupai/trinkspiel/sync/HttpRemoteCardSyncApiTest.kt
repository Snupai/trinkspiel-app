package com.snupai.trinkspiel.sync

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.cardUserIdForName
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket

class HttpRemoteCardSyncApiTest {
    @Test
    fun fetchCardsSendsAuthAndParsesBackendCards() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        var captured: RecordedHttpRequest? = null
        TestHttpServer(
            responseBody = """
                {
                  "cards": [
                    {
                      "remoteId": "remote-fetch-1",
                      "text": "Vom Server",
                      "drinks": 3,
                      "category": "${CardCategory.CHALLENGE.id}",
                      "packName": "Server Pack",
                      "enabled": true,
                      "pendingReview": false,
                      "questionLevel": ${QuestionLevel.LEVEL_2.id},
                      "ownerUserId": "$ownerId",
                      "ownerName": "WG Bibliothek",
                      "contributorName": "Mika",
                      "updatedAtMillis": 123
                    }
                  ]
                }
            """.trimIndent(),
            onRequest = { captured = it },
        ).use { server ->
            val cards = HttpRemoteCardSyncApi().fetchCards(
                config = RemoteSyncConfig(server.baseUrl, "secret-token"),
                ownerUserId = ownerId,
            )

            val request = captured!!
            assertEquals("GET", request.method)
            assertEquals("/libraries/$ownerId/cards", request.path)
            assertEquals("Bearer secret-token", request.headers["authorization"])
            assertEquals(1, cards.size)
            assertEquals("remote-fetch-1", cards.first().remoteId)
            assertEquals("Vom Server", cards.first().text)
            assertEquals(QuestionLevel.LEVEL_2.id, cards.first().questionLevel)
        }
    }

    @Test
    fun pushCardsPostsBatchAndParsesReturnedRemoteIds() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        var captured: RecordedHttpRequest? = null
        TestHttpServer(
            responseBody = """
                {
                  "cards": [
                    {
                      "clientLocalId": 7,
                      "card": {
                        "remoteId": "server-created-7",
                        "text": "Lokaler Vorschlag",
                        "drinks": 4,
                        "category": "${CardCategory.SPICY.id}",
                        "packName": "WG",
                        "enabled": false,
                        "pendingReview": true,
                        "questionLevel": ${QuestionLevel.LEVEL_3.id},
                        "ownerUserId": "$ownerId",
                        "ownerName": "WG Bibliothek",
                        "contributorName": "Mika",
                        "updatedAtMillis": 999
                      }
                    }
                  ]
                }
            """.trimIndent(),
            onRequest = { captured = it },
        ).use { server ->
            val results = HttpRemoteCardSyncApi().pushCards(
                config = RemoteSyncConfig(server.baseUrl, "secret-token"),
                ownerUserId = ownerId,
                requests = listOf(
                    RemoteCardPushRequest(
                        clientLocalId = 7,
                        operation = RemoteCardPushOperation.CREATE,
                        entry = DrinkEntry(
                            id = 7,
                            text = "Lokaler Vorschlag",
                            drinks = 4,
                            category = CardCategory.SPICY.id,
                            packName = "WG",
                            isEnabled = false,
                            isPendingReview = true,
                            questionLevel = QuestionLevel.LEVEL_3.id,
                            ownerUserId = ownerId,
                            ownerName = "WG Bibliothek",
                            contributorUserId = cardUserIdForName("Mika"),
                            contributorName = "Mika",
                            updatedAtMillis = 555,
                        ),
                    )
                ),
            )

            val request = captured!!
            assertEquals("POST", request.method)
            assertEquals("/libraries/$ownerId/cards:batchUpsert", request.path)
            assertEquals("Bearer secret-token", request.headers["authorization"])
            val payload = JSONObject(request.body)
            val pushedCard = payload.getJSONArray("cards").getJSONObject(0)
            assertEquals(7L, pushedCard.getLong("clientLocalId"))
            assertEquals("create", pushedCard.getString("operation"))
            assertEquals("Lokaler Vorschlag", pushedCard.getJSONObject("card").getString("text"))
            assertTrue(pushedCard.getJSONObject("card").getBoolean("pendingReview"))
            assertEquals(1, results.size)
            assertEquals(7L, results.first().clientLocalId)
            assertEquals("server-created-7", results.first().card.remoteId)
            assertEquals(999L, results.first().card.updatedAtMillis)
        }
    }

    @Test
    fun deleteCardsPostsBatchDeleteAndParsesDeletedRemoteIds() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        var captured: RecordedHttpRequest? = null
        TestHttpServer(
            responseBody = """
                {
                  "deletedRemoteIds": ["remote-a"],
                  "skippedRemoteIds": ["remote-missing"]
                }
            """.trimIndent(),
            onRequest = { captured = it },
        ).use { server ->
            val result = HttpRemoteCardSyncApi().deleteCards(
                config = RemoteSyncConfig(server.baseUrl, "admin-token"),
                ownerUserId = ownerId,
                remoteIds = listOf("remote-a", "remote-missing", "remote-a"),
            )

            val request = captured!!
            assertEquals("POST", request.method)
            assertEquals("/libraries/$ownerId/cards:batchDelete", request.path)
            assertEquals("Bearer admin-token", request.headers["authorization"])
            val payload = JSONObject(request.body)
            assertEquals(2, payload.getJSONArray("remoteIds").length())
            assertEquals("remote-a", payload.getJSONArray("remoteIds").getString(0))
            assertEquals("remote-missing", payload.getJSONArray("remoteIds").getString(1))
            assertEquals(listOf("remote-a"), result.deletedRemoteIds)
            assertEquals(listOf("remote-missing"), result.skippedRemoteIds)
        }
    }

    @Test
    fun createBackendInvitePostsContributorAndParsesInviteToken() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        var captured: RecordedHttpRequest? = null
        TestHttpServer(
            responseBody = """
                {
                  "invite": {
                    "type": "seemops.backend_sync_invite",
                    "version": 1,
                    "endpointUrl": "https://sync.example.test/api",
                    "accessToken": "invite-token",
                    "libraryOwnerUserId": "$ownerId",
                    "libraryOwnerName": "WG Bibliothek",
                    "contributorUserId": "account_nora",
                    "contributorName": "Nora",
                    "role": "write"
                  }
                }
            """.trimIndent(),
            onRequest = { captured = it },
        ).use { server ->
            val invite = HttpRemoteCardSyncApi().createBackendInvite(
                config = RemoteSyncConfig(server.baseUrl, "admin-token"),
                ownerUserId = ownerId,
                ownerName = "WG Bibliothek",
                contributorName = "Nora",
                role = "write",
            )

            val request = captured!!
            assertEquals("POST", request.method)
            assertEquals("/libraries/$ownerId/invites", request.path)
            assertEquals("Bearer admin-token", request.headers["authorization"])
            val payload = JSONObject(request.body)
            assertEquals("WG Bibliothek", payload.getString("libraryOwnerName"))
            assertEquals("Nora", payload.getString("contributorName"))
            assertEquals("write", payload.getString("role"))
            assertEquals("invite-token", invite.accessToken)
            assertEquals(ownerId, invite.libraryOwnerUserId)
            assertEquals("account_nora", invite.contributorUserId)
            assertEquals("Nora", invite.contributorName)
            assertEquals("write", invite.role)
        }
    }

    @Test
    fun fetchLibraryMembershipsSendsAuthAndParsesMemberships() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        var captured: RecordedHttpRequest? = null
        TestHttpServer(
            responseBody = """
                {
                  "libraryOwnerUserId": "$ownerId",
                  "memberships": [
                    {
                      "tokenId": "abc123",
                      "libraryOwnerUserId": "$ownerId",
                      "role": "admin",
                      "contributorUserId": "account_owner",
                      "contributorName": "Owner",
                      "source": "configured",
                      "createdAtMillis": 0
                    },
                    {
                      "tokenId": "def456",
                      "libraryOwnerUserId": "$ownerId",
                      "role": "write",
                      "contributorUserId": "account_nora",
                      "contributorName": "Nora",
                      "source": "generated",
                      "createdAtMillis": 123
                    }
                  ]
                }
            """.trimIndent(),
            onRequest = { captured = it },
        ).use { server ->
            val memberships = HttpRemoteCardSyncApi().fetchLibraryMemberships(
                config = RemoteSyncConfig(server.baseUrl, "admin-token"),
                ownerUserId = ownerId,
            )

            val request = captured!!
            assertEquals("GET", request.method)
            assertEquals("/libraries/$ownerId/memberships", request.path)
            assertEquals("Bearer admin-token", request.headers["authorization"])
            assertEquals(2, memberships.size)
            assertEquals("admin", memberships.first().role)
            assertEquals("Owner", memberships.first().contributorName)
            assertEquals("write", memberships[1].role)
            assertEquals("generated", memberships[1].source)
            assertEquals(123L, memberships[1].createdAtMillis)
        }
    }

    @Test
    fun revokeLibraryMembershipDeletesGeneratedMembershipByTokenId() = runBlocking {
        val ownerId = cardUserIdForName("WG Bibliothek")
        var captured: RecordedHttpRequest? = null
        TestHttpServer(
            responseBody = """
                {
                  "revoked": {
                    "tokenId": "def456",
                    "libraryOwnerUserId": "$ownerId",
                    "role": "write",
                    "contributorUserId": "account_nora",
                    "contributorName": "Nora",
                    "source": "generated"
                  }
                }
            """.trimIndent(),
            onRequest = { captured = it },
        ).use { server ->
            val revoked = HttpRemoteCardSyncApi().revokeLibraryMembership(
                config = RemoteSyncConfig(server.baseUrl, "admin-token"),
                ownerUserId = ownerId,
                tokenId = "def456",
            )

            val request = captured!!
            assertEquals("DELETE", request.method)
            assertEquals("/libraries/$ownerId/memberships/def456", request.path)
            assertEquals("Bearer admin-token", request.headers["authorization"])
            assertTrue(revoked)
        }
    }
}

private data class RecordedHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private class TestHttpServer(
    private val responseBody: String,
    private val onRequest: (RecordedHttpRequest) -> Unit,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    private val worker = Thread {
        val socket = serverSocket.accept()
        socket.use { client ->
            onRequest(client.readRequest())
            client.writeJsonResponse(responseBody)
        }
    }.apply {
        isDaemon = true
        start()
    }

    val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"

    override fun close() {
        serverSocket.close()
        worker.join(1_000)
    }
}

private fun Socket.readRequest(): RecordedHttpRequest {
    val reader = getInputStream().bufferedReader(Charsets.UTF_8)
    val requestLine = reader.readLine()
    val parts = requestLine.split(" ")
    val headers = mutableMapOf<String, String>()
    while (true) {
        val line = reader.readLine()
        if (line.isNullOrBlank()) break
        val separatorIndex = line.indexOf(':')
        if (separatorIndex > 0) {
            headers[line.substring(0, separatorIndex).trim().lowercase()] =
                line.substring(separatorIndex + 1).trim()
        }
    }
    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
    val body = if (contentLength > 0) {
        val chars = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = reader.read(chars, read, contentLength - read)
            if (count < 0) break
            read += count
        }
        String(chars, 0, read)
    } else {
        ""
    }
    return RecordedHttpRequest(
        method = parts.getOrElse(0) { "" },
        path = parts.getOrElse(1) { "" },
        headers = headers,
        body = body,
    )
}

private fun Socket.writeJsonResponse(body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    val response = buildString {
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: application/json\r\n")
        append("Content-Length: ${bytes.size}\r\n")
        append("\r\n")
    }.toByteArray(Charsets.UTF_8)
    getOutputStream().use { output ->
        output.write(response)
        output.write(bytes)
        output.flush()
    }
}
