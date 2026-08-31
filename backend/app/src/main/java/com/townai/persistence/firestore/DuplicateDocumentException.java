package com.townai.persistence.firestore;

/** 애플리케이션 수준의 Firestore 고유 키가 이미 예약된 경우 발생한다. */
public class DuplicateDocumentException extends RuntimeException {

    /**
     * 고유 키 중복 오류를 생성한다.
     *
     * @param message 중복된 문서 설명
     */
    public DuplicateDocumentException(String message) {
        super(message);
    }
}
