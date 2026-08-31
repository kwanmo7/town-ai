package com.townai.persistence.firestore;

/** Firestore Client의 Checked Exception을 애플리케이션 영속성 오류로 변환한다. */
public class FirestorePersistenceException extends RuntimeException {

    /**
     * Firestore 영속성 오류를 생성한다.
     *
     * @param message 작업 실패 설명
     * @param cause Firestore Client 원인 예외
     */
    public FirestorePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
