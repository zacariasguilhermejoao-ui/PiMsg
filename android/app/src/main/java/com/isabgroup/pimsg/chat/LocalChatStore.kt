package com.isabgroup.pimsg.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Cache persistente de mensagens e fila de envio local.
 * A base fica no armazenamento privado da aplicação e sobrevive ao fecho/reinício do telefone.
 */
internal class LocalChatStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "pimsg_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                body TEXT,
                media_id TEXT,
                created_at INTEGER NOT NULL,
                expires_at INTEGER,
                pending INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX messages_conversation_time ON messages(conversation_id, created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun read(conversationId: String): List<Message> {
        val result = mutableListOf<Message>()
        readableDatabase.query(
            "messages",
            arrayOf("id", "sender_id", "kind", "body", "media_id", "created_at", "expires_at", "pending"),
            "conversation_id = ? AND (expires_at IS NULL OR expires_at > ?)",
            arrayOf(conversationId, System.currentTimeMillis().toString()),
            null, null, "created_at DESC"
        ).use { c ->
            while (c.moveToNext()) {
                result += Message(
                    id = c.getString(0),
                    senderId = c.getString(1),
                    kind = c.getString(2),
                    body = c.getString(3),
                    mediaId = c.getString(4),
                    createdAt = c.getLong(5),
                    expiresAt = if (c.isNull(6)) null else c.getLong(6),
                    pending = c.getInt(7) == 1,
                )
            }
        }
        return result
    }

    @Synchronized
    fun saveServer(conversationId: String, messages: List<Message>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Faz upsert incremental: mensagens antigas já sincronizadas continuam disponíveis offline.
            // Apenas a fila pendente é preservada sem ser substituída pelo servidor.
            messages.forEach { message ->
                val values = ContentValues().apply {
                    put("id", message.id)
                    put("conversation_id", conversationId)
                    put("sender_id", message.senderId)
                    put("kind", message.kind)
                    put("body", message.body)
                    put("media_id", message.mediaId)
                    put("created_at", message.createdAt)
                    if (message.expiresAt == null) putNull("expires_at") else put("expires_at", message.expiresAt)
                    put("pending", 0)
                }
                db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun enqueue(conversationId: String, message: Message) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("conversation_id", conversationId)
            put("sender_id", message.senderId)
            put("kind", message.kind)
            put("body", message.body)
            put("media_id", message.mediaId)
            put("created_at", message.createdAt)
            putNull("expires_at")
            put("pending", 1)
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun pending(conversationId: String): List<Message> = read(conversationId).filter { it.pending }

    @Synchronized
    fun markSent(localId: String) {
        writableDatabase.delete("messages", "id = ? AND pending = 1", arrayOf(localId))
    }

    @Synchronized
    fun deleteMessage(messageId: String) {
        writableDatabase.delete("messages", "id = ?", arrayOf(messageId))
    }

    @Synchronized
    fun clearPending(conversationId: String) {
        writableDatabase.delete("messages", "conversation_id = ? AND pending = 1", arrayOf(conversationId))
    }
}
