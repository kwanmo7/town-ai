package com.townai.report.storage;

/**
 * Report 저장소 접근 실패를 Service 계층에 전달하는 구현 독립 예외이다.
 *
 * <p>Service는 로컬 파일 또는 GCS의 구체적인 예외 대신 이 타입을
 * {@link com.townai.common.error.ErrorCode#STORAGE_ERROR}로 변환한다.</p>
 */
public class ReportStorageException extends RuntimeException {

    /**
     * Storage 작업 실패와 원인을 함께 보존한다.
     *
     * @param message 실패한 Storage 작업 설명
     * @param cause 파일시스템 또는 외부 Storage에서 발생한 원인
     */
    public ReportStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
