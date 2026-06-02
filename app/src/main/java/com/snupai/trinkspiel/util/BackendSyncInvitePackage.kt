package com.snupai.trinkspiel.util

import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_ID
import com.snupai.trinkspiel.model.DEFAULT_CARD_USER_NAME
import com.snupai.trinkspiel.model.normalizedCardUserId
import com.snupai.trinkspiel.model.normalizedCardUserName
import com.snupai.trinkspiel.sync.RemoteSyncConfig
import com.snupai.trinkspiel.sync.normalizedRemoteSyncRole
import org.json.JSONObject
import org.json.JSONTokener

data class BackendSyncInvite(
    val endpointUrl: String,
    val accessToken: String,
    val libraryOwnerUserId: String,
    val libraryOwnerName: String,
    val contributorUserId: String = DEFAULT_CARD_USER_ID,
    val contributorName: String = DEFAULT_CARD_USER_NAME,
    val role: String = "write",
) {
    fun normalized(): BackendSyncInvite {
        val cleanOwnerName = normalizedCardUserName(libraryOwnerName)
        val cleanContributorName = contributorName.trim().ifBlank { DEFAULT_CARD_USER_NAME }
        return copy(
            endpointUrl = endpointUrl.trim().trimEnd('/'),
            accessToken = accessToken.trim(),
            libraryOwnerUserId = normalizedCardUserId(libraryOwnerUserId, cleanOwnerName),
            libraryOwnerName = cleanOwnerName,
            contributorUserId = normalizedCardUserId(contributorUserId, cleanContributorName),
            contributorName = cleanContributorName,
            role = role.normalizedRemoteSyncRole().ifBlank { "write" },
        )
    }
}

object BackendSyncInvitePackage {
    private const val PACKAGE_TYPE = "seemops.backend_sync_invite"
    private const val PACKAGE_VERSION = 1

    fun toJson(
        config: RemoteSyncConfig,
        libraryOwnerUserId: String,
        libraryOwnerName: String,
        contributorUserId: String,
        contributorName: String,
        role: String = "",
    ): String {
        val invite = BackendSyncInvite(
            endpointUrl = config.endpointUrl,
            accessToken = config.accessToken,
            libraryOwnerUserId = libraryOwnerUserId,
            libraryOwnerName = libraryOwnerName,
            contributorUserId = contributorUserId,
            contributorName = contributorName,
            role = role.ifBlank { config.role },
        ).normalized()
        require(invite.endpointUrl.isNotBlank()) { "Backend-URL fehlt" }
        require(invite.accessToken.isNotBlank()) { "Sync-Token fehlt" }
        val root = JSONObject()
            .put("type", PACKAGE_TYPE)
            .put("version", PACKAGE_VERSION)
            .put("createdAtMillis", System.currentTimeMillis())
            .put("endpointUrl", invite.endpointUrl)
            .put("accessToken", invite.accessToken)
            .put("libraryOwnerUserId", invite.libraryOwnerUserId)
            .put("libraryOwnerName", invite.libraryOwnerName)
            .put("contributorUserId", invite.contributorUserId)
            .put("contributorName", invite.contributorName)
            .put("role", invite.role)
        return root.toString(2)
    }

    fun fromJson(json: String): BackendSyncInvite {
        val root = JSONTokener(json.trim()).nextValue() as? JSONObject
            ?: error("Kein Backend-Invite")
        require(root.optString("type") == PACKAGE_TYPE) { "Kein Backend-Invite" }
        val invite = BackendSyncInvite(
            endpointUrl = root.optString("endpointUrl", root.optString("backendUrl", "")),
            accessToken = root.optString("accessToken", root.optString("syncToken", "")),
            libraryOwnerUserId = root.optString("libraryOwnerUserId", root.optString("ownerUserId", "")),
            libraryOwnerName = root.optString("libraryOwnerName", root.optString("ownerName", "")),
            contributorUserId = root.optString("contributorUserId", DEFAULT_CARD_USER_ID),
            contributorName = root.optString("contributorName", DEFAULT_CARD_USER_NAME),
            role = root.optString("role", "write"),
        ).normalized()
        require(invite.endpointUrl.isNotBlank()) { "Backend-URL fehlt" }
        require(invite.accessToken.isNotBlank()) { "Sync-Token fehlt" }
        return invite
    }

    fun isBackendSyncInvite(json: String): Boolean =
        runCatching {
            val root = JSONTokener(json.trim()).nextValue()
            root is JSONObject && root.optString("type") == PACKAGE_TYPE
        }.getOrDefault(false)

    fun fileName(): String = "seemops_backend_invite.json"
}
