package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.BlockHistoryEvent
import com.example.data.model.BlockedUserRecord
import com.example.data.model.ChatLockConfig
import com.example.data.model.PrivacySettings
import com.example.data.model.PublicProfile
import com.example.data.model.ReportCategory
import com.example.data.model.ReportStatus
import com.example.data.model.SpamGuardState
import com.example.data.model.TwoStepSecurityConfig
import com.example.data.model.UserKeyBundle
import com.example.data.model.UserReport
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
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

class SecurityRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing security settings.")

    // --- End-to-End Encryption Key Bundles ---

    suspend fun publishKeyBundle(bundle: UserKeyBundle): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid == bundle.userId) { "Key bundle UID mismatch" }
        val ref = db.collection("key_bundles").document(uid)
        val snap = ref.get().await()
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "identityPublicKey" to bundle.identityPublicKey,
            "signedPreKeyPublic" to bundle.signedPreKeyPublic,
            "signedPreKeySignature" to bundle.signedPreKeySignature,
            "oneTimePreKeyPublic" to bundle.oneTimePreKeyPublic,
            "keyFingerprint" to bundle.keyFingerprint,
            "protocolVersion" to bundle.protocolVersion,
            "keyVersion" to bundle.keyVersion,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (snap.exists()) {
            payload.remove("userId")
            ref.update(payload).await()
        } else {
            payload["createdAt"] = FieldValue.serverTimestamp()
            ref.set(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "key_bundles/${auth.currentUser?.uid}")
    }

    fun observeKeyBundles(): Flow<Map<String, UserKeyBundle>> = flow {
        val uid = requireUserId()
        val path = "key_bundles"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserKeyBundle::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).associateBy { it.userId.ifBlank { it.id } }
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getKeyBundle(userId: String): UserKeyBundle? {
        return try {
            db.collection("key_bundles").document(userId).get().await()
                .toObject(UserKeyBundle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "key_bundles/$userId")
            null
        }
    }

    // --- Block / Unblock & Block Audit History ---

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUserRecord>> = flow {
        val path = "users/$userId/blocked_users"
        emitAll(
            db.collection("users").document(userId).collection("blocked_users")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BlockedUserRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBlockedByUsers(userId: String): Flow<Set<String>> = flow {
        val path = "users/$userId/blocked_by"
        emitAll(
            db.collection("users").document(userId).collection("blocked_by")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.getString("blockerUid")?.takeIf { it.isNotBlank() } ?: doc.id.takeIf { it.isNotBlank() }
                    }.toSet()
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    emit(emptySet())
                }
        )
    }

    fun observeBlockHistory(userId: String): Flow<List<BlockHistoryEvent>> = flow {
        val path = "users/$userId/block_history"
        emitAll(
            db.collection("users").document(userId).collection("block_history")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BlockHistoryEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun blockUser(
        target: PublicProfile,
        reason: String = "Blocked by user"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(target.userId.isNotBlank() && target.userId != uid) { "Cannot block this user" }
        val userDoc = db.collection("users").document(uid)
        val blockRef = userDoc.collection("blocked_users").document(target.userId)
        val snap = blockRef.get().await()
        val safeName = target.displayName.ifBlank { "User" }.take(60)
        val safeHandle = target.handle.take(40)
        val safeReason = reason.trim().ifBlank { "Blocked by user" }.take(200)

        if (snap.exists()) {
            blockRef.update(
                mapOf(
                    "blockedDisplayName" to safeName,
                    "blockedHandle" to safeHandle,
                    "reason" to safeReason,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            blockRef.set(
                mapOf(
                    "blockerUid" to uid,
                    "blockedUid" to target.userId,
                    "blockedDisplayName" to safeName,
                    "blockedHandle" to safeHandle,
                    "reason" to safeReason,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("users").document(target.userId)
                .collection("blocked_by").document(uid)
                .set(
                    mapOf(
                        "blockerUid" to uid,
                        "blockedUid" to target.userId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }

        val eventId = "blk_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        userDoc.collection("block_history").document(eventId).set(
            mapOf(
                "blockerUid" to uid,
                "targetUid" to target.userId,
                "targetDisplayName" to safeName,
                "action" to "blocked",
                "reason" to safeReason,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/blocked_users")
    }

    suspend fun unblockUser(
        targetUid: String,
        targetDisplayName: String,
        reason: String = "Unblocked by user"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        userDoc.collection("blocked_users").document(targetUid).delete().await()
        runCatching {
            db.collection("users").document(targetUid)
                .collection("blocked_by").document(uid)
                .delete().await()
        }

        val eventId = "ublk_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        userDoc.collection("block_history").document(eventId).set(
            mapOf(
                "blockerUid" to uid,
                "targetUid" to targetUid,
                "targetDisplayName" to targetDisplayName.ifBlank { "User" }.take(60),
                "action" to "unblocked",
                "reason" to reason.trim().ifBlank { "Unblocked by user" }.take(200),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/blocked_users/$targetUid")
    }

    // --- Privacy Settings ---

    fun observePrivacySettings(userId: String): Flow<PrivacySettings> = flow {
        val path = "users/$userId/security/privacy_settings"
        emitAll(
            db.collection("users").document(userId).collection("security").document("privacy_settings")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) PrivacySettings()
                    else snapshot.toObject(
                        PrivacySettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: PrivacySettings()
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun savePrivacySettings(settings: PrivacySettings): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("security").document("privacy_settings")
        val payload = hashMapOf<String, Any>(
            "docType" to "privacy_settings",
            "lastSeenVisibility" to settings.lastSeenVisibility,
            "onlineVisibility" to settings.onlineVisibility,
            "screenshotMode" to settings.screenshotMode,
            "defaultDisappearingSeconds" to settings.defaultDisappearingSeconds,
            "spamProtectionLevel" to settings.spamProtectionLevel,
            "e2eeEnabled" to settings.e2eeEnabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        upsertSecurityDoc(ref, payload)

        // Sync visibility preferences & masked online status to public_profiles
        val publicRef = db.collection("public_profiles").document(uid)
        val publicSnap = publicRef.get().await()
        if (publicSnap.exists()) {
            val publicUpdates = mutableMapOf<String, Any>(
                "lastSeenVisibility" to settings.lastSeenVisibility,
                "onlineVisibility" to settings.onlineVisibility,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (com.example.security.PrivacyVisibilityResolver.shouldHideOnlineFromPublicDoc(
                    onlineVisibility = settings.onlineVisibility,
                    lastSeenVisibility = settings.lastSeenVisibility
                )
            ) {
                publicUpdates["isOnline"] = false
                publicUpdates["activeSessions"] = emptyMap<String, Any>()
            }
            publicRef.update(publicUpdates).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/security/privacy_settings")
    }

    // --- Two-Step Verification ---

    fun observeTwoStepConfig(userId: String): Flow<TwoStepSecurityConfig?> = flow {
        val path = "users/$userId/security/two_step"
        emitAll(
            db.collection("users").document(userId).collection("security").document("two_step")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        TwoStepSecurityConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveTwoStepConfig(config: TwoStepSecurityConfig): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("security").document("two_step")
        val payload = hashMapOf<String, Any>(
            "docType" to "two_step",
            "enabled" to config.enabled,
            "pinHash" to config.pinHash.take(256),
            "pinSalt" to config.pinSalt.take(128),
            "recoveryEmail" to config.recoveryEmail.trim().take(200),
            "recoveryCodeHash" to config.recoveryCodeHash.take(256),
            "recoveryCodeSalt" to config.recoveryCodeSalt.take(128),
            "registeredDeviceIds" to config.registeredDeviceIds.distinct().take(50),
            "failedAttempts" to config.failedAttempts.coerceIn(0, 1000),
            "lockedUntilEpochMs" to config.lockedUntilEpochMs.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        upsertSecurityDoc(ref, payload)
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/security/two_step")
    }

    // --- Chat Lock ---

    fun observeChatLockConfig(userId: String): Flow<ChatLockConfig> = flow {
        val path = "users/$userId/security/chat_lock"
        emitAll(
            db.collection("users").document(userId).collection("security").document("chat_lock")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) ChatLockConfig()
                    else snapshot.toObject(
                        ChatLockConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: ChatLockConfig()
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveChatLockConfig(config: ChatLockConfig): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("security").document("chat_lock")
        val payload = hashMapOf<String, Any>(
            "docType" to "chat_lock",
            "lockedChatIds" to config.lockedChatIds.distinct().take(200),
            "biometricEnabled" to config.biometricEnabled,
            "lockPinHash" to config.lockPinHash.take(256),
            "lockPinSalt" to config.lockPinSalt.take(128),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        upsertSecurityDoc(ref, payload)
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/security/chat_lock")
    }

    // --- Spam Guard ---

    fun observeSpamGuardState(userId: String): Flow<SpamGuardState> = flow {
        val path = "users/$userId/security/spam_guard"
        emitAll(
            db.collection("users").document(userId).collection("security").document("spam_guard")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) SpamGuardState()
                    else snapshot.toObject(
                        SpamGuardState::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: SpamGuardState()
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveSpamGuardState(state: SpamGuardState): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("security").document("spam_guard")
        val payload = hashMapOf<String, Any>(
            "docType" to "spam_guard",
            "restrictedUntilEpochMs" to state.restrictedUntilEpochMs.coerceAtLeast(0L),
            "violationCount" to state.violationCount.coerceIn(0, 10000),
            "lastViolationReason" to state.lastViolationReason.take(200),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        upsertSecurityDoc(ref, payload)
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/security/spam_guard")
    }

    // --- Reports & Admin Review ---

    fun observeIsAdmin(userId: String): Flow<Boolean> = flow {
        val path = "admins/$userId"
        emitAll(
            db.collection("admins").document(userId)
                .snapshots()
                .map { snapshot -> snapshot.exists() }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (fsErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(false)
                    } else {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                        emit(false)
                    }
                }
        )
    }

    fun observeMyReports(userId: String): Flow<List<UserReport>> = flow {
        val path = "reports"
        emitAll(
            db.collection(path)
                .whereEqualTo("reporterId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllReportsForAdmin(): Flow<List<UserReport>> = flow {
        val uid = requireUserId()
        val path = "reports"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsErr = unwrapFirestore(error)
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitReport(
        reporterName: String,
        reportedUserId: String,
        reportedUserName: String,
        category: ReportCategory,
        description: String,
        chatId: String = "",
        messageId: String = "",
        messagePreview: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val payload = hashMapOf<String, Any>(
            "reporterId" to uid,
            "reporterName" to reporterName.trim().ifBlank { "Pulse User" }.take(60),
            "reportedUserId" to reportedUserId.trim().ifBlank { uid }.take(128),
            "reportedUserName" to reportedUserName.trim().ifBlank { "Account" }.take(60),
            "category" to category.wireValue,
            "description" to description.trim().take(1000),
            "status" to ReportStatus.PENDING.wireValue,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (chatId.isNotBlank()) payload["chatId"] = chatId.take(128)
        if (messageId.isNotBlank()) payload["messageId"] = messageId.take(128)
        if (messagePreview.isNotBlank()) payload["messagePreview"] = messagePreview.take(300)

        db.collection("reports").document(reportId).set(payload).await()
        reportId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "reports")
    }

    suspend fun reviewReport(
        reportId: String,
        status: ReportStatus,
        reviewNotes: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("reports").document(reportId).update(
            mapOf(
                "status" to status.wireValue,
                "reviewNotes" to reviewNotes.trim().take(500),
                "reviewedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId")
    }

    private suspend fun upsertSecurityDoc(
        ref: DocumentReference,
        payload: MutableMap<String, Any>
    ) {
        val existing = ref.get().await()
        if (existing.exists()) {
            ref.update(payload).await()
        } else {
            payload["createdAt"] = FieldValue.serverTimestamp()
            ref.set(payload).await()
        }
    }

    private fun unwrapFirestore(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
