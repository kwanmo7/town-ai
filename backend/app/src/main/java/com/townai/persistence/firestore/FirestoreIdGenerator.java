package com.townai.persistence.firestore;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.LongStream;

/** Allocates API-compatible numeric identifiers with a transactional counter. */
@Component
public class FirestoreIdGenerator {

    private static final String LAST_ID = "lastId";

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;

    public FirestoreIdGenerator(
            Firestore firestore,
            FirestoreTransactionRunner transactions
    ) {
        this.firestore = firestore;
        this.transactions = transactions;
    }

    public long next(String namespace) {
        return nextRange(namespace, 1).findFirst().orElseThrow();
    }

    /**
     * Reserves a contiguous identifier range with one counter read and write.
     * This keeps batched creates valid because Firestore transactions require
     * every read to happen before the first write.
     */
    public LongStream nextRange(String namespace, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (!transactions.isActive()) {
            long[] range = transactions.execute(
                    () -> nextRange(namespace, count).toArray()
            );
            return LongStream.of(range);
        }

        DocumentReference counter = firestore
                .collection(FirestoreCollections.COUNTERS)
                .document(namespace);
        DocumentSnapshot snapshot = transactions.get(counter);
        Long previous = snapshot.exists() ? snapshot.getLong(LAST_ID) : null;
        long first = previous == null ? 1L : Math.addExact(previous, 1L);
        long last = Math.addExact(first, count - 1L);
        transactions.set(counter, Map.of(LAST_ID, last));
        return LongStream.rangeClosed(first, last);
    }
}
