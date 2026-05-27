package com.example.lab4.chat

import android.content.Context
import com.example.lab4.firebase.FirebaseAvailability
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update

interface ChatRepository {

    fun observeMessages(limit: Long = 50): Flow<List<ChatMessage>>

    fun sendMessage(
        userId: String,
        userName: String,
        text: String,
        lat: Double? = null,
        lon: Double? = null,
        accuracyM: Float? = null,
    )

    companion object {
        fun create(context: Context): ChatRepository =
            if (FirebaseAvailability.isConfigured(context)) FirestoreChatRepository()
            else InMemoryChatRepository()
    }
}

private class FirestoreChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ChatRepository {
    private val messages = firestore.collection("messages")

    override fun observeMessages(limit: Long): Flow<List<ChatMessage>> = callbackFlow {
        val registration = messages
            .orderBy("timestampMs", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents.orEmpty().mapNotNull { doc ->
                    val userId = doc.getString("userId") ?: return@mapNotNull null
                    val userName = doc.getString("userName") ?: "User"
                    val text = doc.getString("text") ?: ""
                    val ts = doc.getLong("timestampMs") ?: 0L
                    val lat = doc.getDouble("lat")
                    val lon = doc.getDouble("lon")
                    val acc = doc.getDouble("accuracyM")?.toFloat()
                    ChatMessage(
                        id = doc.id,
                        userId = userId,
                        userName = userName,
                        text = text,
                        timestampMs = ts,
                        lat = lat,
                        lon = lon,
                        accuracyM = acc,
                    )
                }.sortedBy { it.timestampMs }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override fun sendMessage(
        userId: String,
        userName: String,
        text: String,
        lat: Double?,
        lon: Double?,
        accuracyM: Float?,
    ) {
        val data = hashMapOf<String, Any?>(
            "userId" to userId,
            "userName" to userName,
            "text" to text,
            "timestampMs" to System.currentTimeMillis(),
            "lat" to lat,
            "lon" to lon,
            "accuracyM" to accuracyM?.toDouble(),
        )
        messages.add(data)
    }
}

private class InMemoryChatRepository : ChatRepository {
    private val state = MutableStateFlow<List<ChatMessage>>(emptyList())
    override fun observeMessages(limit: Long): Flow<List<ChatMessage>> =
        state.asStateFlow()

    override fun sendMessage(
        userId: String,
        userName: String,
        text: String,
        lat: Double?,
        lon: Double?,
        accuracyM: Float?,
    ) {
        val msg = ChatMessage(
            id = System.nanoTime().toString(),
            userId = userId,
            userName = userName,
            text = text,
            timestampMs = System.currentTimeMillis(),
            lat = lat,
            lon = lon,
            accuracyM = accuracyM,
        )
        state.update { (it + msg).takeLast(100) }
    }
}

