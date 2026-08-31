package com.townai.persistence.firestore;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.LongStream;

/** 기존 API와 호환되는 숫자 ID를 Firestore Counter Transaction으로 발급한다. */
@Component
public class FirestoreIdGenerator {

    private static final String LAST_ID = "lastId";

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;

    /**
     * 숫자 ID 발급기를 생성한다.
     *
     * @param firestore Firestore Client
     * @param transactions Transaction 실행기
     */
    public FirestoreIdGenerator(
            Firestore firestore,
            FirestoreTransactionRunner transactions
    ) {
        this.firestore = firestore;
        this.transactions = transactions;
    }

    /**
     * Namespace의 다음 숫자 ID 하나를 Transaction으로 발급한다.
     *
     * @param namespace Entity별 Counter 문서 이름
     * @return 새로 발급한 숫자 ID
     */
    public long next(String namespace) {
        return nextRange(namespace, 1).findFirst().orElseThrow();
    }

    /**
     * Counter를 한 번 읽고 써서 연속된 ID 범위를 예약한다.
     *
     * <p>Firestore Transaction은 첫 쓰기 전에 모든 읽기를 끝내야 한다. 일괄 생성에서 ID를
     * 하나씩 발급하면 읽기와 쓰기가 교차하므로, 필요한 범위를 먼저 확보한다.</p>
     *
     * @param namespace Entity별 Counter 문서 이름
     * @param count 예약할 ID 개수
     * @return 작은 값부터 정렬된 연속 ID Stream
     */
    public LongStream nextRange(String namespace, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (!transactions.isActive()) {
            // 호출자가 Transaction을 시작하지 않았어도 Counter 증가는 반드시 원자적으로 실행한다.
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
