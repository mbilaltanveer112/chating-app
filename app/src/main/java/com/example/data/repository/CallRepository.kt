package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.IceCandidateRecord
import com.example.data.model.PublicProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepository(
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
            ?: throw IllegalStateException("User must be signed in before starting or joining a call.")

    suspend fun createCallSession(
        chatId: String,
        caller: PublicProfile,
        callee: PublicProfile,
        callType: CallType,
        offerSdp: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid == caller.userId) { "Caller UID mismatch" }
        require(uid != callee.userId) { "Cannot place a call to yourself" }
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val payload = hashMapOf<String, Any>(
            "chatId" to chatId,
            "callerId" to uid,
            "callerName" to caller.displayName.ifBlank { "Pulse User" }.take(50),
            "calleeId" to callee.userId,
            "calleeName" to callee.displayName.ifBlank { "Contact" }.take(50),
            "participantUids" to listOf(uid, callee.userId),
            "callType" to callType.wireValue,
            "status" to CallStatus.CALLING.wireValue,
            "offerSdp" to offerSdp.take(65000),
            "durationSeconds" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (caller.avatarData.isNotBlank()) payload["callerAvatarData"] = caller.avatarData
        if (caller.photoUrl.isNotBlank()) payload["callerPhotoUrl"] = caller.photoUrl.take(500)
        if (callee.avatarData.isNotBlank()) payload["calleeAvatarData"] = callee.avatarData
        if (callee.photoUrl.isNotBlank()) payload["calleePhotoUrl"] = callee.photoUrl.take(500)

        db.collection("calls").document(callId).set(payload).await()
        callId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "calls")
    }

    suspend fun getCallSession(callId: String): CallSession? {
        return try {
            db.collection("calls").document(callId).get().await()
                .toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "calls/$callId")
            null
        }
    }

    suspend fun updateCallAnswer(callId: String, answerSdp: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("calls").document(callId).update(
            mapOf(
                "answerSdp" to answerSdp.take(65000),
                "status" to CallStatus.CONNECTING.wireValue,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
    }

    suspend fun updateCallOfferSdp(callId: String, offerSdp: String, status: CallStatus? = null): Result<Unit> = runCatching {
        requireUserId()
        val updates = mutableMapOf<String, Any>(
            "offerSdp" to offerSdp.take(65000),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (status != null) {
            updates["status"] = status.wireValue
        }
        db.collection("calls").document(callId).update(updates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
    }

    suspend fun updateCallStatus(
        callId: String,
        status: CallStatus,
        endedBy: String? = null,
        endReason: String? = null,
        markStartedNow: Boolean = false,
        markEndedNow: Boolean = false,
        durationSeconds: Long? = null
    ): Result<Unit> = runCatching {
        requireUserId()
        val updates = mutableMapOf<String, Any>(
            "status" to status.wireValue,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!endedBy.isNullOrBlank()) updates["endedBy"] = endedBy
        if (!endReason.isNullOrBlank()) updates["endReason"] = endReason.take(80)
        if (markStartedNow) updates["startedAt"] = FieldValue.serverTimestamp()
        if (markEndedNow) updates["endedAt"] = FieldValue.serverTimestamp()
        if (durationSeconds != null) updates["durationSeconds"] = durationSeconds.coerceIn(0L, 86400L)

        db.collection("calls").document(callId).update(updates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
    }

    suspend fun sendIceCandidate(
        callId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val candidateId = "ice_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        db.collection("calls")
            .document(callId)
            .collection("candidates")
            .document(candidateId)
            .set(
                mapOf(
                    "senderId" to uid,
                    "participantUids" to participantUids,
                    "sdpMid" to sdpMid.take(64),
                    "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
                    "sdp" to sdp.take(2048),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "calls/$callId/candidates")
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = flow {
        requireUserId()
        val path = "calls/$callId"
        emitAll(
            db.collection("calls").document(callId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observeCallHistory(currentUserId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        val path = "calls"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeIceCandidates(callId: String): Flow<List<IceCandidateRecord>> = flow {
        val uid = requireUserId()
        val path = "calls/$callId/candidates"
        emitAll(
            db.collection("calls")
                .document(callId)
                .collection("candidates")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        IceCandidateRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun deleteCallHistoryEntry(callId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("calls").document(callId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "calls/$callId")
    }
}
