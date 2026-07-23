package com.townai.report.storage;

/**
 * Report 본문을 저장하는 외부 저장소의 공통 계약이다.
 *
 * <p>메서드에 전달되는 경로는 DB에 저장되는 논리 객체 경로이며,
 * 구현체가 로컬 파일 경로나 Cloud Storage 객체 경로로 변환한다.</p>
 */
public interface ReportStorage {

    /**
     * UTF-8 Markdown 본문을 지정한 논리 경로에 저장한다.
     *
     * @param storagePath DB에 기록할 Storage 객체 경로
     * @param content 저장할 Markdown 본문
     * @throws ReportStorageException 쓰기에 실패한 경우
     */
    void write(String storagePath, String content);

    /**
     * 저장된 Markdown 본문을 읽는다.
     *
     * @param storagePath 읽을 Storage 객체 경로
     * @return 저장된 UTF-8 Markdown 본문
     * @throws ReportStorageException 객체가 없거나 읽기에 실패한 경우
     */
    String read(String storagePath);

    /**
     * 객체가 이미 없어도 성공으로 처리한다.
     *
     * @param storagePath 삭제할 Storage 객체 경로
     * @throws ReportStorageException 삭제 작업에 실패한 경우
     */
    void delete(String storagePath);
}
