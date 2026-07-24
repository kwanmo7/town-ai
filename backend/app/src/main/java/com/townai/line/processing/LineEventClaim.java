package com.townai.line.processing;

import com.townai.line.model.LineWebhookEventWorkItem;

/**
 * 이벤트 점유 상태와 성공적으로 점유한 Work Item을 함께 반환한다.
 *
 * @param status 점유 결과
 * @param workItem {@code ACQUIRED}일 때만 존재하는 처리 Snapshot
 */
public record LineEventClaim(
        LineEventClaimStatus status,
        LineWebhookEventWorkItem workItem
) {

    /**
     * Work Item을 포함한 점유 성공 결과를 생성한다.
     *
     * @param workItem 현재 시도가 처리할 이벤트
     * @return 점유 성공 결과
     */
    public static LineEventClaim acquired(
            LineWebhookEventWorkItem workItem
    ) {
        return new LineEventClaim(
                LineEventClaimStatus.ACQUIRED,
                workItem
        );
    }

    /**
     * Work Item이 없는 점유 결과를 생성한다.
     *
     * @param status {@code ACQUIRED}가 아닌 상태
     * @return 처리 Snapshot이 없는 점유 결과
     */
    public static LineEventClaim withoutWork(
            LineEventClaimStatus status
    ) {
        if (status == LineEventClaimStatus.ACQUIRED) {
            throw new IllegalArgumentException(
                    "ACQUIRED claim requires a work item."
            );
        }
        return new LineEventClaim(status, null);
    }
}
