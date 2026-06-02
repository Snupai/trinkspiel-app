package com.snupai.trinkspiel.sync

import com.snupai.trinkspiel.data.DrinkEntry
import com.snupai.trinkspiel.model.CardCategory
import com.snupai.trinkspiel.model.QuestionLevel
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RemoteSyncConfig(
    val endpointUrl: String = "",
    val accessToken: String = "",
    val role: String = "",
) {
    val isConfigured: Boolean
        get() = endpointUrl.trim().isNotBlank()

    fun normalized(): RemoteSyncConfig =
        copy(
            endpointUrl = endpointUrl.trim().trimEnd('/'),
            accessToken = accessToken.trim(),
            role = role.normalizedRemoteSyncRole(),
        )
}

fun String.normalizedRemoteSyncRole(): String =
    trim()
        .lowercase()
        .takeIf { it in setOf("read", "write", "admin") }
        .orEmpty()

enum class RemoteCardPushOperation(val id: String) {
    CREATE("create"),
    UPDATE("update"),
}

data class RemoteCardPushRequest(
    val clientLocalId: Long,
    val operation: RemoteCardPushOperation,
    val entry: DrinkEntry,
)

data class RemoteCardPushResult(
    val clientLocalId: Long,
    val card: RemoteCardSnapshot,
)

data class RemoteCardDeleteResult(
    val deletedRemoteIds: List<String>,
    val skippedRemoteIds: List<String>,
)

data class RemoteBackendInvite(
    val endpointUrl: String,
    val accessToken: String,
    val libraryOwnerUserId: String,
    val libraryOwnerName: String,
    val contributorUserId: String,
    val contributorName: String,
    val role: String,
)

data class RemoteLibraryMembership(
    val tokenId: String,
    val libraryOwnerUserId: String,
    val role: String,
    val contributorUserId: String,
    val contributorName: String,
    val source: String,
    val createdAtMillis: Long,
)

interface RemoteCardSyncApi {
    suspend fun fetchCards(
        config: RemoteSyncConfig,
        ownerUserId: String,
    ): List<RemoteCardSnapshot>

    suspend fun pushCards(
        config: RemoteSyncConfig,
        ownerUserId: String,
        requests: List<RemoteCardPushRequest>,
    ): List<RemoteCardPushResult>

    suspend fun deleteCards(
        config: RemoteSyncConfig,
        ownerUserId: String,
        remoteIds: List<String>,
    ): RemoteCardDeleteResult

    suspend fun createBackendInvite(
        config: RemoteSyncConfig,
        ownerUserId: String,
        ownerName: String,
        contributorName: String,
        role: String,
    ): RemoteBackendInvite

    suspend fun fetchLibraryMemberships(
        config: RemoteSyncConfig,
        ownerUserId: String,
    ): List<RemoteLibraryMembership>

    suspend fun revokeLibraryMembership(
        config: RemoteSyncConfig,
        ownerUserId: String,
        tokenId: String,
    ): Boolean
}

class HttpRemoteCardSyncApi : RemoteCardSyncApi {
    override suspend fun fetchCards(
        config: RemoteSyncConfig,
        ownerUserId: String,
    ): List<RemoteCardSnapshot> = withContext(Dispatchers.IO) {
        val response = openConnection(config, cardsUrl(config, ownerUserId), method = "GET")
            .readResponse()
        RemoteCardJson.fromJson(response)
    }

    override suspend fun pushCards(
        config: RemoteSyncConfig,
        ownerUserId: String,
        requests: List<RemoteCardPushRequest>,
    ): List<RemoteCardPushResult> = withContext(Dispatchers.IO) {
        if (requests.isEmpty()) return@withContext emptyList()
        val payload = JSONObject()
            .put(
                "cards",
                org.json.JSONArray().apply {
                    requests.forEach { request ->
                        put(request.toJsonObject())
                    }
                }
            )
            .toString()
        val response = openConnection(config, batchUpsertUrl(config, ownerUserId), method = "POST")
            .apply {
                doOutput = true
                outputStream.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
            .readResponse()
        response.toPushResults()
    }

    override suspend fun deleteCards(
        config: RemoteSyncConfig,
        ownerUserId: String,
        remoteIds: List<String>,
    ): RemoteCardDeleteResult = withContext(Dispatchers.IO) {
        val cleanRemoteIds = remoteIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (cleanRemoteIds.isEmpty()) {
            return@withContext RemoteCardDeleteResult(emptyList(), emptyList())
        }
        val payload = JSONObject()
            .put(
                "remoteIds",
                org.json.JSONArray().apply {
                    cleanRemoteIds.forEach { remoteId -> put(remoteId) }
                }
            )
            .toString()
        val response = openConnection(config, batchDeleteUrl(config, ownerUserId), method = "POST")
            .apply {
                doOutput = true
                outputStream.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
            .readResponse()
        response.toDeleteResult()
    }

    override suspend fun createBackendInvite(
        config: RemoteSyncConfig,
        ownerUserId: String,
        ownerName: String,
        contributorName: String,
        role: String,
    ): RemoteBackendInvite = withContext(Dispatchers.IO) {
        val normalized = config.normalized()
        val payload = JSONObject()
            .put("libraryOwnerName", ownerName.trim())
            .put("contributorName", contributorName.trim())
            .put("role", role.normalizedRemoteSyncRole().ifBlank { "write" })
            .toString()
        val response = openConnection(normalized, invitesUrl(normalized, ownerUserId), method = "POST")
            .apply {
                doOutput = true
                outputStream.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                }
            }
            .readResponse()
        response.toRemoteBackendInvite(defaultEndpointUrl = normalized.endpointUrl)
    }

    override suspend fun fetchLibraryMemberships(
        config: RemoteSyncConfig,
        ownerUserId: String,
    ): List<RemoteLibraryMembership> = withContext(Dispatchers.IO) {
        val normalized = config.normalized()
        val response = openConnection(normalized, membershipsUrl(normalized, ownerUserId), method = "GET")
            .readResponse()
        response.toRemoteLibraryMemberships()
    }

    override suspend fun revokeLibraryMembership(
        config: RemoteSyncConfig,
        ownerUserId: String,
        tokenId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanTokenId = tokenId.trim()
        require(cleanTokenId.isNotBlank()) { "Membership-ID fehlt" }
        val normalized = config.normalized()
        openConnection(
            normalized,
            membershipUrl(normalized, ownerUserId, cleanTokenId),
            method = "DELETE",
        ).readResponse()
        true
    }

    private fun openConnection(
        config: RemoteSyncConfig,
        url: String,
        method: String,
    ): HttpURLConnection {
        val normalized = config.normalized()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (normalized.accessToken.isNotBlank()) {
            connection.setRequestProperty("Authorization", "Bearer ${normalized.accessToken}")
        }
        return connection
    }

    private fun cardsUrl(config: RemoteSyncConfig, ownerUserId: String): String =
        "${config.normalized().endpointUrl}/libraries/${ownerUserId.urlEncoded()}/cards"

    private fun batchUpsertUrl(config: RemoteSyncConfig, ownerUserId: String): String =
        "${cardsUrl(config, ownerUserId)}:batchUpsert"

    private fun batchDeleteUrl(config: RemoteSyncConfig, ownerUserId: String): String =
        "${cardsUrl(config, ownerUserId)}:batchDelete"

    private fun invitesUrl(config: RemoteSyncConfig, ownerUserId: String): String =
        "${config.normalized().endpointUrl}/libraries/${ownerUserId.urlEncoded()}/invites"

    private fun membershipsUrl(config: RemoteSyncConfig, ownerUserId: String): String =
        "${config.normalized().endpointUrl}/libraries/${ownerUserId.urlEncoded()}/memberships"

    private fun membershipUrl(config: RemoteSyncConfig, ownerUserId: String, tokenId: String): String =
        "${membershipsUrl(config, ownerUserId)}/${tokenId.urlEncoded()}"
}

private fun RemoteCardPushRequest.toJsonObject(): JSONObject =
    JSONObject()
        .put("clientLocalId", clientLocalId)
        .put("operation", operation.id)
        .put("card", entry.toRemotePushJsonObject())

private fun DrinkEntry.toRemotePushJsonObject(): JSONObject {
    val ownerName = normalizedCardUserName(ownerName)
    val contributorName = contributorName.trim().ifBlank { ownerName }
    val obj = JSONObject()
        .put("text", text.trim())
        .put("drinks", drinks.coerceAtLeast(1))
        .put("category", CardCategory.fromId(category).id)
        .put("packName", packName.trim().ifBlank { "Eigene Karten" })
        .put("enabled", isEnabled)
        .put("pendingReview", isPendingReview)
        .put("questionLevel", QuestionLevel.fromId(questionLevel).id)
        .put("ownerUserId", normalizedCardUserId(ownerUserId, ownerName))
        .put("ownerName", ownerName)
        .put("contributorUserId", normalizedCardUserId(contributorUserId, contributorName))
        .put("contributorName", contributorName)
        .put("updatedAtMillis", updatedAtMillis.coerceAtLeast(0L))
    remoteId.trim().takeIf { it.isNotBlank() }?.let { remoteId ->
        obj.put("remoteId", remoteId)
    }
    return obj
}

private fun String.toPushResults(): List<RemoteCardPushResult> {
    val root = JSONObject(this)
    val cards = root.optJSONArray("cards") ?: return emptyList()
    return (0 until cards.length()).mapNotNull { index ->
        val obj = cards.optJSONObject(index) ?: return@mapNotNull null
        val cardObject = obj.optJSONObject("card") ?: obj
        val card = RemoteCardJson.run { cardObject.toRemoteCardOrNull() } ?: return@mapNotNull null
        RemoteCardPushResult(
            clientLocalId = obj.optLong("clientLocalId", 0L),
            card = card,
        )
    }
}

private fun String.toDeleteResult(): RemoteCardDeleteResult {
    val root = JSONObject(this)
    return RemoteCardDeleteResult(
        deletedRemoteIds = root.optJSONArray("deletedRemoteIds").toStringList(),
        skippedRemoteIds = root.optJSONArray("skippedRemoteIds").toStringList(),
    )
}

private fun String.toRemoteBackendInvite(defaultEndpointUrl: String): RemoteBackendInvite {
    val root = JSONObject(this)
    val invite = root.optJSONObject("invite") ?: root
    val contributorName = invite.optString("contributorName", "").trim()
    val ownerName = invite.optString("libraryOwnerName", "").trim()
    return RemoteBackendInvite(
        endpointUrl = invite.optString("endpointUrl", defaultEndpointUrl).trim().ifBlank { defaultEndpointUrl },
        accessToken = invite.optString("accessToken", invite.optString("syncToken", "")).trim(),
        libraryOwnerUserId = invite.optString("libraryOwnerUserId", invite.optString("ownerUserId", "")).trim(),
        libraryOwnerName = ownerName,
        contributorUserId = invite.optString("contributorUserId", "").trim(),
        contributorName = contributorName,
        role = invite.optString("role", "write").normalizedRemoteSyncRole().ifBlank { "write" },
    )
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index -> optString(index).trim().takeIf { it.isNotBlank() } }
}

private fun String.toRemoteLibraryMemberships(): List<RemoteLibraryMembership> {
    val root = JSONObject(this)
    val memberships = root.optJSONArray("memberships") ?: return emptyList()
    return (0 until memberships.length()).mapNotNull { index ->
        val obj = memberships.optJSONObject(index) ?: return@mapNotNull null
        RemoteLibraryMembership(
            tokenId = obj.optString("tokenId", "").trim(),
            libraryOwnerUserId = obj.optString("libraryOwnerUserId", "").trim(),
            role = obj.optString("role", "").normalizedRemoteSyncRole(),
            contributorUserId = obj.optString("contributorUserId", "").trim(),
            contributorName = obj.optString("contributorName", "").trim(),
            source = obj.optString("source", "configured").trim().ifBlank { "configured" },
            createdAtMillis = obj.optLong("createdAtMillis", 0L).coerceAtLeast(0L),
        )
    }
}

private fun HttpURLConnection.readResponse(): String {
    val responseCode = responseCode
    val stream = if (responseCode in 200..299) inputStream else errorStream
    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (responseCode !in 200..299) {
        error("Remote-Sync fehlgeschlagen ($responseCode): ${body.take(240)}")
    }
    return body
}

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, "UTF-8")
