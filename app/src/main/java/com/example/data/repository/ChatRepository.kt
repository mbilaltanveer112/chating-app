package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.MessageType
import com.example.data.model.PublicProfile
import com.example.data.model.UserPrivate
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- User Profile & Directory ---

    suspend fun doesUserProfileExist(userId: String): Boolean {
        return try {
            db.collection("users").document(userId).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId")
            false
        }
    }

    private fun sanitizeDeviceKey(deviceId: String): String =
        deviceId.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifBlank { "primary" }
            .take(64)

    suspend fun registerPhoneUserProfile(
        displayName: String,
        phoneNumber: String,
        bio: String = "Available on PulseChat",
        avatarData: String? = null,
        deviceId: String = "primary"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeName = displayName.trim().ifBlank { "Pulse User" }.take(50)
        val safeBio = bio.trim().ifBlank { "Available on PulseChat" }.take(200)
        val safeHandle = safeName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" }.take(32)
        val safePhone = phoneNumber.trim().take(24)
        val safeDeviceId = sanitizeDeviceKey(deviceId)

        val userRef = db.collection("users").document(uid)
        val publicRef = db.collection("public_profiles").document(uid)

        val existingPrivate = try {
            userRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (existingPrivate != null && existingPrivate.exists()) {
            val updates = mutableMapOf<String, Any>(
                "displayName" to safeName,
                "bio" to safeBio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safePhone.isNotBlank()) updates["phoneNumber"] = safePhone
            if (!avatarData.isNullOrBlank()) updates["avatarData"] = avatarData
            userRef.update(updates).await()
        } else {
            val privatePayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "phoneNumber" to safePhone,
                "displayName" to safeName,
                "bio" to safeBio,
                "darkModeEnabled" to true,
                "notificationsEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarData.isNullOrBlank()) {
                privatePayload["avatarData"] = avatarData
            }
            userRef.set(privatePayload).await()
        }

        val existingPublic = try {
            publicRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (existingPublic != null && existingPublic.exists()) {
            val existingOnlineVis = existingPublic.getString("onlineVisibility")
            val existingLastSeenVis = existingPublic.getString("lastSeenVisibility")
            val hideOnline = com.example.security.PrivacyVisibilityResolver.shouldHideOnlineFromPublicDoc(
                onlineVisibility = existingOnlineVis,
                lastSeenVisibility = existingLastSeenVis
            )
            val pubUpdates = mutableMapOf<String, Any>(
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to safeBio,
                "isOnline" to !hideOnline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!hideOnline) {
                pubUpdates["lastHeartbeatAt"] = FieldValue.serverTimestamp()
                pubUpdates["activeSessions.$safeDeviceId"] = FieldValue.serverTimestamp()
            }
            if (existingLastSeenVis != "nobody") {
                pubUpdates["lastSeen"] = FieldValue.serverTimestamp()
            }
            if (!avatarData.isNullOrBlank()) pubUpdates["avatarData"] = avatarData
            publicRef.update(pubUpdates).await()
        } else {
            val publicPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to safeBio,
                "isOnline" to true,
                "discoverable" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                "activeSessions" to mapOf(safeDeviceId to FieldValue.serverTimestamp()),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarData.isNullOrBlank()) {
                publicPayload["avatarData"] = avatarData
            }
            publicRef.set(publicPayload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    suspend fun syncExistingPhoneUser(
        phoneNumber: String,
        deviceId: String = "primary"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safePhone = phoneNumber.trim().take(24)
        val safeDeviceId = sanitizeDeviceKey(deviceId)
        val userRef = db.collection("users").document(uid)
        val publicRef = db.collection("public_profiles").document(uid)

        val userUpdates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (safePhone.isNotBlank()) {
            userUpdates["phoneNumber"] = safePhone
        }
        userRef.update(userUpdates).await()

        val pubSnap = try {
            publicRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (pubSnap != null && pubSnap.exists()) {
            val existingOnlineVis = pubSnap.getString("onlineVisibility")
            val existingLastSeenVis = pubSnap.getString("lastSeenVisibility")
            val hideOnline = com.example.security.PrivacyVisibilityResolver.shouldHideOnlineFromPublicDoc(
                onlineVisibility = existingOnlineVis,
                lastSeenVisibility = existingLastSeenVis
            )
            val updates = mutableMapOf<String, Any>(
                "isOnline" to !hideOnline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!hideOnline) {
                updates["lastHeartbeatAt"] = FieldValue.serverTimestamp()
                updates["activeSessions.$safeDeviceId"] = FieldValue.serverTimestamp()
            }
            if (existingLastSeenVis != "nobody") {
                updates["lastSeen"] = FieldValue.serverTimestamp()
            }
            publicRef.update(updates).await()
        } else {
            val existingUser = userRef.get().await()
            val name = existingUser.getString("displayName")?.takeIf { it.isNotBlank() } ?: "Pulse User"
            val bio = existingUser.getString("bio").orEmpty().ifBlank { "Available on PulseChat" }
            val avatarData = existingUser.getString("avatarData").orEmpty()
            val handle = name.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" }.take(32)
            val publicPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to name,
                "handle" to handle,
                "bio" to bio,
                "isOnline" to true,
                "discoverable" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                "activeSessions" to mapOf(safeDeviceId to FieldValue.serverTimestamp()),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarData.isNotBlank()) {
                publicPayload["avatarData"] = avatarData
            }
            publicRef.set(publicPayload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        photoUrl: String = "",
        phoneNumber: String = "",
        deviceId: String = "primary"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeName = displayName.trim().ifBlank { "Pulse User" }.take(50)
        val safeHandle = safeName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" }.take(32)
        val safePhone = phoneNumber.trim().take(24)
        val safeDeviceId = sanitizeDeviceKey(deviceId)

        val userRef = db.collection("users").document(uid)
        val publicRef = db.collection("public_profiles").document(uid)

        val existingPrivate = try {
            userRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (existingPrivate != null && existingPrivate.exists()) {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val existingEmail = existingPrivate.getString("email").orEmpty()
            val existingPhone = existingPrivate.getString("phoneNumber").orEmpty()
            if (email.isNotBlank() && existingEmail.isBlank()) updates["email"] = email.take(200)
            if (safePhone.isNotBlank() && existingPhone.isBlank()) updates["phoneNumber"] = safePhone
            userRef.update(updates).await()
        } else {
            val privatePayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "email" to email.take(200),
                "displayName" to safeName,
                "bio" to "Available on PulseChat",
                "darkModeEnabled" to true,
                "notificationsEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (safePhone.isNotBlank()) {
                privatePayload["phoneNumber"] = safePhone
            }
            if (photoUrl.isNotBlank()) {
                privatePayload["photoUrl"] = photoUrl.take(500)
            }
            userRef.set(privatePayload).await()
        }

        val existingPublic = try {
            publicRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (existingPublic != null && existingPublic.exists()) {
            val existingOnlineVis = existingPublic.getString("onlineVisibility")
            val existingLastSeenVis = existingPublic.getString("lastSeenVisibility")
            val hideOnline = com.example.security.PrivacyVisibilityResolver.shouldHideOnlineFromPublicDoc(
                onlineVisibility = existingOnlineVis,
                lastSeenVisibility = existingLastSeenVis
            )
            val updates = mutableMapOf<String, Any>(
                "isOnline" to !hideOnline,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!hideOnline) {
                updates["lastHeartbeatAt"] = FieldValue.serverTimestamp()
                updates["activeSessions.$safeDeviceId"] = FieldValue.serverTimestamp()
            }
            if (existingLastSeenVis != "nobody") {
                updates["lastSeen"] = FieldValue.serverTimestamp()
            }
            publicRef.update(updates).await()
        } else {
            val publicPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to safeName,
                "handle" to safeHandle,
                "bio" to "Available on PulseChat",
                "isOnline" to true,
                "discoverable" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "lastHeartbeatAt" to FieldValue.serverTimestamp(),
                "activeSessions" to mapOf(safeDeviceId to FieldValue.serverTimestamp()),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (photoUrl.isNotBlank()) {
                publicPayload["photoUrl"] = photoUrl.take(500)
            }
            publicRef.set(publicPayload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    suspend fun updatePresence(
        isOnline: Boolean,
        onlineVisibility: String? = null,
        lastSeenVisibility: String? = null,
        deviceId: String = "primary"
    ): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        val safeDeviceId = sanitizeDeviceKey(deviceId)
        val publicRef = db.collection("public_profiles").document(uid)
        val snap = publicRef.get().await()
        if (snap.exists()) {
            val effectiveOnlineVis = onlineVisibility
                ?: snap.getString("onlineVisibility")
                ?: "everyone"
            val effectiveLastSeenVis = lastSeenVisibility
                ?: snap.getString("lastSeenVisibility")
                ?: "everyone"
            val hideOnline = com.example.security.PrivacyVisibilityResolver.shouldHideOnlineFromPublicDoc(
                onlineVisibility = effectiveOnlineVis,
                lastSeenVisibility = effectiveLastSeenVis
            )

            @Suppress("UNCHECKED_CAST")
            val rawSessions = (snap.get("activeSessions") as? Map<String, Any?>).orEmpty()
            val nowMs = System.currentTimeMillis()
            val timeoutMs = com.example.security.PrivacyVisibilityResolver.ONLINE_HEARTBEAT_TIMEOUT_MS
            val staleKeys = rawSessions.entries.mapNotNull { (key, value) ->
                val ts = (value as? Timestamp)?.toDate()?.time
                if (ts != null && (nowMs - ts) > timeoutMs) key else null
            }

            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (hideOnline) {
                updates["isOnline"] = false
                updates["activeSessions"] = emptyMap<String, Any>()
                if (!isOnline && effectiveLastSeenVis != "nobody") {
                    updates["lastSeen"] = FieldValue.serverTimestamp()
                }
            } else if (isOnline) {
                updates["isOnline"] = true
                updates["lastHeartbeatAt"] = FieldValue.serverTimestamp()
                updates["activeSessions.$safeDeviceId"] = FieldValue.serverTimestamp()
                staleKeys.filter { it != safeDeviceId }.forEach { staleKey ->
                    updates["activeSessions.$staleKey"] = FieldValue.delete()
                }
            } else {
                val otherActiveCount = rawSessions.entries.count { (key, value) ->
                    if (key == safeDeviceId) return@count false
                    val ts = (value as? Timestamp)?.toDate()?.time
                    ts == null || (nowMs - ts) <= timeoutMs
                }
                if (rawSessions.containsKey(safeDeviceId)) {
                    updates["activeSessions.$safeDeviceId"] = FieldValue.delete()
                }
                staleKeys.forEach { staleKey ->
                    updates["activeSessions.$staleKey"] = FieldValue.delete()
                }
                updates["isOnline"] = otherActiveCount > 0
                if (otherActiveCount == 0 && effectiveLastSeenVis != "nobody") {
                    updates["lastSeen"] = FieldValue.serverTimestamp()
                }
            }

            publicRef.update(updates).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "public_profiles/${auth.currentUser?.uid}")
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        avatarData: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanName = displayName.trim().ifBlank { "Pulse User" }.take(50)
        val cleanBio = bio.trim().take(200)
        val cleanHandle = cleanName.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "user" }.take(32)

        val userUpdates = mutableMapOf<String, Any>(
            "displayName" to cleanName,
            "bio" to cleanBio,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val publicUpdates = mutableMapOf<String, Any>(
            "displayName" to cleanName,
            "handle" to cleanHandle,
            "bio" to cleanBio,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (avatarData != null) {
            userUpdates["avatarData"] = avatarData
            publicUpdates["avatarData"] = avatarData
        }

        db.collection("users").document(uid).update(userUpdates).await()
        db.collection("public_profiles").document(uid).update(publicUpdates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    suspend fun updatePreferences(
        darkModeEnabled: Boolean? = null,
        notificationsEnabled: Boolean? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        darkModeEnabled?.let { updates["darkModeEnabled"] = it }
        notificationsEnabled?.let { updates["notificationsEnabled"] = it }
        db.collection("users").document(uid).update(updates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    suspend fun updateChatTheme(chatId: String, themeSpec: String?): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
        val cleanSpec = themeSpec?.trim()?.take(120).orEmpty()
        val fieldValue: Any = if (cleanSpec.isEmpty()) {
            FieldValue.delete()
        } else {
            cleanSpec
        }
        db.collection("users").document(uid).update(
            mapOf(
                "chatThemes.$chatId" to fieldValue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    fun observePrivateUser(userId: String): Flow<UserPrivate?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserPrivate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestore(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeDiscoverableUsers(): Flow<List<PublicProfile>> = flow {
        val uid = requireUserId()
        val path = "public_profiles"
        emitAll(
            db.collection(path)
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PublicProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestore(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicProfile(userId: String): Flow<PublicProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "public_profiles/$userId"
        emitAll(
            db.collection("public_profiles").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        PublicProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestore(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    emit(null)
                }
        )
    }

    // --- Chats ---

    fun observeChats(userId: String): Flow<List<ChatRoom>> = flow {
        val path = "chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<ChatRoom> { it.isPinnedBy(userId) }
                            .thenByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestore(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeChat(chatId: String): Flow<ChatRoom?> = flow {
        val uid = requireUserId()
        val path = "chats/$chatId"
        emitAll(
            db.collection("chats").document(chatId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        ChatRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestore(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createOrGetDirectChat(
        currentUser: PublicProfile,
        otherUser: PublicProfile,
        defaultDisappearingSeconds: Long = 0L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val isSelfChat = currentUser.userId == otherUser.userId
        val existingChats = db.collection("chats")
            .whereArrayContains("memberUids", uid)
            .get()
            .await()
            .toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

        val found = existingChats.firstOrNull { room ->
            if (isSelfChat) {
                room.type == "direct" && room.memberUids == listOf(uid)
            } else {
                room.type == "direct" &&
                    room.memberUids.size == 2 &&
                    room.memberUids.containsAll(listOf(uid, otherUser.userId))
            }
        }
        if (found != null) return@runCatching found.id

        val members = if (isSelfChat) listOf(uid) else listOf(uid, otherUser.userId).distinct()
        val memberNames = if (isSelfChat) {
            mapOf(uid to currentUser.displayName.ifBlank { "Me" })
        } else {
            mapOf(
                uid to currentUser.displayName.ifBlank { "Me" },
                otherUser.userId to otherUser.displayName.ifBlank { "User" }
            )
        }
        val chatId = "dm_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val title = if (isSelfChat) "Saved Notes" else otherUser.displayName.ifBlank { "Direct Chat" }
        val payload = mutableMapOf<String, Any>(
            "type" to "direct",
            "title" to title.take(80),
            "memberUids" to members,
            "memberNames" to memberNames,
            "createdBy" to uid,
            "lastMessageText" to "",
            "lastMessageType" to "text",
            "lastMessageSenderId" to "",
            "pinnedBy" to emptyList<String>(),
            "archivedBy" to emptyList<String>(),
            "typingUids" to emptyList<String>(),
            "typingTimestamps" to emptyMap<String, Any>(),
            "deliveredByUids" to listOf(uid),
            "readByUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (defaultDisappearingSeconds in setOf(86_400L, 604_800L, 2_592_000L)) {
            payload["disappearingDurationSeconds"] = defaultDisappearingSeconds
            payload["disappearingUpdatedBy"] = uid
            payload["disappearingUpdatedAt"] = FieldValue.serverTimestamp()
        }
        db.collection("chats").document(chatId).set(payload).await()
        chatId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "chats")
    }

    suspend fun createGroupChat(
        title: String,
        currentUser: PublicProfile,
        selectedMembers: List<PublicProfile>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val allProfiles = (listOf(currentUser) + selectedMembers).distinctBy { it.userId }.take(20)
        val memberUids = allProfiles.map { it.userId }
        val memberNames = allProfiles.associate { it.userId to it.displayName.ifBlank { "Member" } }
        val chatId = "grp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val cleanTitle = title.trim().ifBlank { "New Group" }.take(80)

        val payload = mapOf(
            "type" to "group",
            "title" to cleanTitle,
            "memberUids" to memberUids,
            "memberNames" to memberNames,
            "createdBy" to uid,
            "lastMessageText" to "${currentUser.displayName} created group \"$cleanTitle\"",
            "lastMessageType" to "text",
            "lastMessageSenderId" to uid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "pinnedBy" to emptyList<String>(),
            "archivedBy" to emptyList<String>(),
            "typingUids" to emptyList<String>(),
            "typingTimestamps" to emptyMap<String, Any>(),
            "deliveredByUids" to listOf(uid),
            "readByUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("chats").document(chatId).set(payload).await()
        chatId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "chats")
    }

    suspend fun togglePinChat(chatId: String, currentlyPinned: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val op = if (currentlyPinned) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
        db.collection("chats").document(chatId).update(
            mapOf(
                "pinnedBy" to op,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
    }

    suspend fun toggleArchiveChat(chatId: String, currentlyArchived: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val op = if (currentlyArchived) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
        db.collection("chats").document(chatId).update(
            mapOf(
                "archivedBy" to op,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
    }

    suspend fun archiveChats(chatIds: List<String>, archive: Boolean = true): Result<Int> = runCatching {
        val uid = requireUserId()
        val uniqueIds = chatIds.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return@runCatching 0
        val op = if (archive) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        val chatsCol = db.collection("chats")
        uniqueIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id ->
                batch.update(
                    chatsCol.document(id),
                    mapOf(
                        "archivedBy" to op,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
        uniqueIds.size
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats")
    }

    suspend fun markChatAsDelivered(chatId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("chats").document(chatId).update(
            mapOf(
                "deliveredByUids" to FieldValue.arrayUnion(uid),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
    }

    suspend fun markChatAsRead(chatId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("chats").document(chatId).update(
            mapOf(
                "deliveredByUids" to FieldValue.arrayUnion(uid),
                "readByUids" to FieldValue.arrayUnion(uid),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
    }

    suspend fun setTyping(chatId: String, isTyping: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val updates = if (isTyping) {
            mapOf(
                "typingUids" to FieldValue.arrayUnion(uid),
                "typingTimestamps.$uid" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "typingUids" to FieldValue.arrayRemove(uid),
                "typingTimestamps.$uid" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
        db.collection("chats").document(chatId).update(updates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
    }

    suspend fun deleteChat(chatId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("chats").document(chatId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "chats/$chatId")
    }

    // --- Messages ---

    fun observeMessages(chatId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestore(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateDisappearingTimer(
        chatId: String,
        memberUids: List<String>,
        actorName: String,
        durationSeconds: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(durationSeconds in setOf(0L, 86_400L, 604_800L, 2_592_000L)) {
            "Invalid disappearing message duration"
        }
        db.collection("chats").document(chatId).update(
            mapOf(
                "disappearingDurationSeconds" to durationSeconds,
                "disappearingUpdatedBy" to uid,
                "disappearingUpdatedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val label = when (durationSeconds) {
            86_400L -> "24 Hours"
            604_800L -> "7 Days"
            2_592_000L -> "30 Days"
            else -> "Off"
        }
        val notice = if (durationSeconds == 0L) {
            "${actorName.ifBlank { "Someone" }} turned off disappearing messages."
        } else {
            "${actorName.ifBlank { "Someone" }} set disappearing messages to $label."
        }
        sendMessage(
            chatId = chatId,
            memberUids = memberUids,
            senderName = actorName,
            type = MessageType.SYSTEM,
            text = notice,
            systemEventType = "disappearing_timer"
        ).getOrThrow()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
    }

    suspend fun sendScreenshotNotice(
        chatId: String,
        memberUids: List<String>,
        actorName: String,
        formattedDateTime: String
    ): Result<String> {
        val uid = requireUserId()
        val safeActor = actorName.trim().ifBlank { "Participant" }
        val text = "$safeActor took a screenshot."
        return sendMessage(
            chatId = chatId,
            memberUids = memberUids,
            senderName = safeActor,
            type = MessageType.SYSTEM,
            text = "$text • $formattedDateTime",
            systemEventType = "screenshot",
            screenshotActorId = uid
        )
    }

    suspend fun purgeExpiredMessages(chatId: String, expiredMessageIds: List<String>): Result<Unit> = runCatching {
        requireUserId()
        val ids = expiredMessageIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return@runCatching Unit
        val col = db.collection("chats").document(chatId).collection("messages")
        ids.chunked(200).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { msgId ->
                batch.delete(col.document(msgId))
            }
            batch.commit().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "chats/$chatId/messages")
    }

    suspend fun sendMessage(
        chatId: String,
        memberUids: List<String>,
        senderName: String,
        type: MessageType,
        text: String = "",
        mediaData: String? = null,
        mediaFileName: String? = null,
        mediaMimeType: String? = null,
        mediaSizeBytes: Long? = null,
        durationMs: Long? = null,
        waveform: List<Int>? = null,
        replyTo: ChatMessage? = null,
        forwardedFrom: String? = null,
        messageIdOverride: String? = null,
        isEncrypted: Boolean = false,
        encryptionVersion: Int = 0,
        encryptedPayload: String? = null,
        encryptedMediaData: String? = null,
        wrappedKeys: Map<String, Map<String, String>>? = null,
        expiresAtEpochMs: Long = 0L,
        systemEventType: String? = null,
        screenshotActorId: String? = null,
        chatPreviewOverride: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val messageId = messageIdOverride?.takeIf { it.isNotBlank() }
            ?: "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)

        val payload = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "senderId" to uid,
            "senderName" to senderName.trim().ifBlank { "User" }.take(50),
            "memberUids" to memberUids.distinct().take(20),
            "type" to type.wireValue,
            "text" to text.take(4000),
            "deliveredByUids" to listOf(uid),
            "deliveredTimestamps" to mapOf(uid to FieldValue.serverTimestamp()),
            "readByUids" to listOf(uid),
            "deleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        mediaData?.takeIf { it.isNotBlank() && it.length <= 450000 }?.let {
            payload["mediaData"] = it
        }
        mediaFileName?.takeIf { it.isNotBlank() }?.let {
            payload["mediaFileName"] = it.take(200)
        }
        mediaMimeType?.takeIf { it.isNotBlank() }?.let {
            payload["mediaMimeType"] = it.take(100)
        }
        mediaSizeBytes?.takeIf { it >= 0 }?.let {
            payload["mediaSizeBytes"] = it
        }
        durationMs?.takeIf { it >= 0 }?.let {
            payload["durationMs"] = it
        }
        waveform?.takeIf { it.isNotEmpty() }?.let {
            payload["waveform"] = it.take(64)
        }
        if (replyTo != null && replyTo.id.isNotBlank()) {
            payload["replyToMessageId"] = replyTo.id.take(128)
            payload["replyToSenderName"] = replyTo.senderName.take(50)
            payload["replyToPreview"] = if (isEncrypted) {
                ""
            } else {
                buildPreviewText(replyTo.messageType, replyTo.text, replyTo.mediaFileName).take(200)
            }
        }
        forwardedFrom?.takeIf { it.isNotBlank() }?.let {
            payload["forwardedFrom"] = it.take(50)
        }
        if (isEncrypted) {
            payload["isEncrypted"] = true
            payload["encryptionVersion"] = encryptionVersion.coerceIn(1, 10)
            encryptedPayload?.takeIf { it.isNotBlank() && it.length <= 16000 }?.let {
                payload["encryptedPayload"] = it
            }
            encryptedMediaData?.takeIf { it.isNotBlank() && it.length <= 650000 }?.let {
                payload["encryptedMediaData"] = it
            }
            if (!wrappedKeys.isNullOrEmpty()) {
                payload["wrappedKeys"] = wrappedKeys
            }
        }
        if (expiresAtEpochMs > 0L) {
            payload["expiresAtEpochMs"] = expiresAtEpochMs
        }
        systemEventType?.takeIf { it.isNotBlank() }?.let {
            payload["systemEventType"] = it.take(40)
        }
        screenshotActorId?.takeIf { it.isNotBlank() }?.let {
            payload["screenshotActorId"] = it.take(128)
        }

        msgRef.set(payload).await()

        val preview = (chatPreviewOverride ?: buildPreviewText(type, text, mediaFileName.orEmpty())).take(300)
        db.collection("chats").document(chatId).update(
            mapOf(
                "lastMessageText" to preview,
                "lastMessageType" to type.wireValue,
                "lastMessageSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "deliveredByUids" to listOf(uid),
                "readByUids" to listOf(uid),
                "typingUids" to FieldValue.arrayRemove(uid),
                "typingTimestamps.$uid" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        messageId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "chats/$chatId/messages")
    }

    suspend fun markMessagesDelivered(chatId: String, messageIds: List<String>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val uniqueIds = messageIds.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return@runCatching Unit

        val messagesCol = db.collection("chats").document(chatId).collection("messages")
        uniqueIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { msgId ->
                batch.update(
                    messagesCol.document(msgId),
                    mapOf(
                        "deliveredByUids" to FieldValue.arrayUnion(uid),
                        "deliveredTimestamps.$uid" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages")
    }

    suspend fun markUndeliveredMessagesInChat(chatId: String, limit: Int = 50): Result<Int> = runCatching {
        val uid = requireUserId()
        markChatAsDelivered(chatId).getOrThrow()

        val snapshot = db.collection("chats").document(chatId).collection("messages")
            .whereArrayContains("memberUids", uid)
            .get()
            .await()

        val undeliveredIds = snapshot.toObjects(ChatMessage::class.java)
            .asSequence()
            .filter {
                it.id.isNotBlank() &&
                    it.senderId != uid &&
                    !it.deliveredByUids.contains(uid) &&
                    !it.readByUids.contains(uid)
            }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            .take(limit.coerceAtLeast(1))
            .map { it.id }
            .toList()

        if (undeliveredIds.isNotEmpty()) {
            markMessagesDelivered(chatId, undeliveredIds).getOrThrow()
        }
        undeliveredIds.size
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages")
    }

    suspend fun markMessageRead(chatId: String, messageId: String): Result<Unit> =
        markMessagesRead(chatId, listOf(messageId))

    suspend fun markMessagesRead(
        chatId: String,
        messageIds: List<String>,
        alsoRecordDeliveredTimestampIds: Set<String> = emptySet()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val uniqueIds = messageIds.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return@runCatching Unit

        val messagesCol = db.collection("chats").document(chatId).collection("messages")
        uniqueIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { msgId ->
                val updates = mutableMapOf<String, Any>(
                    "deliveredByUids" to FieldValue.arrayUnion(uid),
                    "readByUids" to FieldValue.arrayUnion(uid),
                    "readTimestamps.$uid" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (alsoRecordDeliveredTimestampIds.contains(msgId)) {
                    updates["deliveredTimestamps.$uid"] = FieldValue.serverTimestamp()
                }
                batch.update(messagesCol.document(msgId), updates)
            }
            batch.commit().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages")
    }

    suspend fun toggleReaction(
        chatId: String,
        message: ChatMessage,
        emoji: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val updated = message.reactions.toMutableMap()
        if (updated[uid] == emoji) {
            updated.remove(uid)
        } else {
            updated[uid] = emoji
        }
        db.collection("chats").document(chatId)
            .collection("messages").document(message.id)
            .update(
                mapOf(
                    "reactions" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages/${message.id}")
    }

    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("chats").document(chatId)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "text" to "This message was deleted",
                    "deleted" to true,
                    "mediaData" to "",
                    "encryptedPayload" to "",
                    "encryptedMediaData" to "",
                    "wrappedKeys" to emptyMap<String, Any>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages/$messageId")
    }

    suspend fun forwardMessage(
        targetChat: ChatRoom,
        senderName: String,
        original: ChatMessage
    ): Result<String> {
        return sendMessage(
            chatId = targetChat.id,
            memberUids = targetChat.memberUids,
            senderName = senderName,
            type = original.messageType,
            text = original.text,
            mediaData = original.mediaData.takeIf { it.isNotBlank() },
            mediaFileName = original.mediaFileName.takeIf { it.isNotBlank() },
            mediaMimeType = original.mediaMimeType.takeIf { it.isNotBlank() },
            mediaSizeBytes = original.mediaSizeBytes.takeIf { it > 0 },
            durationMs = original.durationMs.takeIf { it > 0 },
            waveform = original.waveform.takeIf { it.isNotEmpty() },
            forwardedFrom = original.senderName
        )
    }

    private fun buildPreviewText(type: MessageType, text: String, fileName: String): String {
        return when (type) {
            MessageType.TEXT -> text.ifBlank { "Message" }
            MessageType.IMAGE -> if (text.isNotBlank()) "Photo: $text" else "Photo"
            MessageType.AI_IMAGE -> if (text.isNotBlank()) "AI Image: $text" else "AI Image"
            MessageType.VIDEO -> if (text.isNotBlank()) "Video: $text" else "Video"
            MessageType.VOICE -> "Voice note"
            MessageType.DOCUMENT -> if (fileName.isNotBlank()) "Document: $fileName" else "Document"
            MessageType.CALL -> text.ifBlank { "Call" }
            MessageType.SYSTEM -> text.ifBlank { "Security notice" }
        }
    }

    private fun unwrapFirestore(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
