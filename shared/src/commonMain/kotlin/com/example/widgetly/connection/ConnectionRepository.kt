package com.example.widgetly.connection

import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConnectionRepository {
    fun observeConnection(connectionId: String): Flow<Connection?>
}

/**
 * Reads live from Firestore's `connections/{connectionId}` doc. The [firestore]
 * instance passed in must already be bound to the `widgetly` named database
 * (via `Firebase.firestore(app, "widgetly")`) - see the named-database note in
 * this task's report, the client does not have a `(default)` database to fall
 * back to.
 */
class FirestoreConnectionRepository(private val firestore: FirebaseFirestore) : ConnectionRepository {
    override fun observeConnection(connectionId: String): Flow<Connection?> =
        firestore.collection("connections").document(connectionId).snapshots
            .map { snapshot ->
                if (!snapshot.exists) null else connectionFromDocument(snapshot.id, snapshot.data())
            }
}
