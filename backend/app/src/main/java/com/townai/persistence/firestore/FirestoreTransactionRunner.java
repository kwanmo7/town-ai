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
 * 하나의 요청 흐름에 참여하는 Repository가 동일한 Firestore Transaction을 공유하도록 한다.
 *
 * <p>Service가 Transaction 안에서 여러 Repository를 호출해도 중첩 Transaction을 만들지 않고,
 * 현재 Thread에 연결된 Transaction을 재사용한다. Transaction 밖에서 호출된 단순 조회·저장은
 * Firestore API를 직접 실행한다.</p>
 */
@Component
public class FirestoreTransactionRunner {

    private final Firestore firestore;
    private final ThreadLocal<Transaction> current = new ThreadLocal<>();

    /**
     * Transaction 실행기를 생성한다.
     *
     * @param firestore Server-side Firestore Client
     */
    public FirestoreTransactionRunner(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * 작업을 현재 Transaction에 참여시키거나 새 Transaction으로 실행한다.
     *
     * @param work 실행할 작업
     * @param <T> 작업 결과 타입
     * @return 작업 결과
     */
    public <T> T execute(Supplier<T> work) {
        // 이미 Transaction 안이라면 바깥 Transaction의 read-before-write 순서를 그대로 유지한다.
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

    /**
     * 반환값이 없는 작업을 현재 Transaction에 참여시키거나 새 Transaction으로 실행한다.
     *
     * @param work 실행할 작업
     */
    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    /**
     * 현재 Transaction 유무에 맞춰 문서 한 건을 읽는다.
     *
     * @param reference 읽을 문서 참조
     * @return 문서 Snapshot
     */
    public DocumentSnapshot get(DocumentReference reference) {
        Transaction transaction = current.get();
        return await(transaction == null
                ? reference.get()
                : transaction.get(reference));
    }

    /**
     * 현재 Transaction 유무에 맞춰 Query 결과를 읽는다.
     *
     * @param query 실행할 Firestore Query
     * @return Query Snapshot
     */
    public QuerySnapshot get(Query query) {
        Transaction transaction = current.get();
        return await(transaction == null ? query.get() : transaction.get(query));
    }

    /**
     * 문서 전체를 현재 Transaction에 쓰거나 즉시 저장한다.
     *
     * @param reference 저장할 문서 참조
     * @param data 문서 전체 필드
     */
    public void set(DocumentReference reference, Map<String, Object> data) {
        Transaction transaction = current.get();
        if (transaction == null) {
            await(reference.set(data));
        } else {
            transaction.set(reference, data);
        }
    }

    /**
     * 기존 문서의 다른 필드는 유지하고 전달된 필드만 병합한다.
     *
     * @param reference 저장할 문서 참조
     * @param data 병합할 필드
     */
    public void merge(DocumentReference reference, Map<String, Object> data) {
        Transaction transaction = current.get();
        if (transaction == null) {
            await(reference.set(data, SetOptions.merge()));
        } else {
            transaction.set(reference, data, SetOptions.merge());
        }
    }

    /**
     * 문서를 현재 Transaction에서 삭제하거나 즉시 삭제한다.
     *
     * @param reference 삭제할 문서 참조
     */
    public void delete(DocumentReference reference) {
        Transaction transaction = current.get();
        if (transaction == null) {
            await(reference.delete());
        } else {
            transaction.delete(reference);
        }
    }

    /**
     * 현재 Thread가 Firestore Transaction을 수행 중인지 반환한다.
     *
     * @return Transaction 안이면 {@code true}
     */
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
            // Firestore SDK가 감싼 Domain 예외를 원래 타입으로 복원해야 상위 오류 처리가 유지된다.
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
