package com.townai.persistence.firestore;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Transaction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Runs repository work in one Firestore transaction and shares the transaction
 * with every repository participating on the current thread.
 */
@Component
public class FirestoreTransactionRunner {

    private final Firestore firestore;
    private final ThreadLocal<Transaction> current = new ThreadLocal<>();

    public FirestoreTransactionRunner(Firestore firestore) {
        this.firestore = firestore;
    }

    public <T> T execute(Supplier<T> work) {
        if (current.get() != null) {
            return work.get();
        }
        return await(firestore.runTransaction(transaction -> {
            current.set(transaction);
            try {
                return work.get();
            } catch (RuntimeException exception) {
                throw new TransactionWorkException(exception);
            } finally {
                current.remove();
            }
        }));
    }

    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    public DocumentSnapshot get(DocumentReference reference) {
        Transaction transaction = current.get();
        return await(transaction == null
                ? reference.get()
                : transaction.get(reference));
    }

    public QuerySnapshot get(Query query) {
        Transaction transaction = current.get();
        return await(transaction == null ? query.get() : transaction.get(query));
    }

    public void set(DocumentReference reference, Map<String, Object> data) {
        Transaction transaction = current.get();
        if (transaction == null) {
            await(reference.set(data));
        } else {
            transaction.set(reference, data);
        }
    }

    public void merge(DocumentReference reference, Map<String, Object> data) {
        Transaction transaction = current.get();
        if (transaction == null) {
            await(reference.set(data, SetOptions.merge()));
        } else {
            transaction.set(reference, data, SetOptions.merge());
        }
    }

    public void delete(DocumentReference reference) {
        Transaction transaction = current.get();
        if (transaction == null) {
            await(reference.delete());
        } else {
            transaction.delete(reference);
        }
    }

    public boolean isActive() {
        return current.get() != null;
    }

    private <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FirestorePersistenceException(
                    "Interrupted while waiting for Firestore.",
                    exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null
                    ? exception
                    : exception.getCause();
            RuntimeException workFailure = findWorkFailure(cause);
            if (workFailure != null) {
                throw workFailure;
            }
            throw new FirestorePersistenceException(
                    "Firestore operation failed.",
                    cause
            );
        }
    }

    private RuntimeException findWorkFailure(Throwable failure) {
        Throwable currentFailure = failure;
        while (currentFailure != null) {
            if (currentFailure instanceof TransactionWorkException wrapper) {
                return wrapper.workFailure;
            }
            currentFailure = currentFailure.getCause();
        }
        return null;
    }

    private static final class TransactionWorkException
            extends RuntimeException {

        private final RuntimeException workFailure;

        private TransactionWorkException(RuntimeException workFailure) {
            super(workFailure);
            this.workFailure = workFailure;
        }
    }
}
