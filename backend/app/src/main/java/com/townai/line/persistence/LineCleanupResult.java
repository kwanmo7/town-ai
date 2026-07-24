package com.townai.line.persistence;

/**
 * 한 번의 LINE 처리 데이터 정리 결과이다.
 *
 * @param deletedDrafts 삭제한 Visit Draft 수
 * @param deletedEvents 삭제한 완료·실패 Webhook Event 수
 */
public record LineCleanupResult(
        int deletedDrafts,
        int deletedEvents
) {
}
