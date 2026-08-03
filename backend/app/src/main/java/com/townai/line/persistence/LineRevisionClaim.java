package com.townai.line.persistence;

import com.townai.line.entity.LineVisitDraftEntity;

/**
 * 수정 Text Message가 원본 Draft를 점유한 결과이다.
 *
 * @param status 점유 결과
 * @param source 점유에 성공한 원본 Draft. 그 외에는 {@code null}
 */
public record LineRevisionClaim(
        Status status,
        LineVisitDraftEntity source
) {

    /** 수정 점유 결과 종류이다. */
    public enum Status {
        /** 수정 대기 Draft가 없어 일반 신규 입력으로 처리해야 한다. */
        NONE,
        /** 현재 Text Message가 수정 원본을 독점적으로 점유했다. */
        CLAIMED,
        /** 다른 Text Message가 이미 사용자의 수정 요청을 처리하고 있다. */
        BUSY,
        /** 수정 의도는 기록됐지만 원본 상태가 바뀌어 더 이상 적용할 수 없다. */
        INVALID
    }

    /**
     * 수정 대상이 없는 결과를 만든다.
     *
     * @return 수정 대상 없음 결과
     */
    public static LineRevisionClaim none() {
        return new LineRevisionClaim(Status.NONE, null);
    }

    /**
     * 원본 Draft 점유 성공 결과를 만든다.
     *
     * @param source 점유한 원본 Draft
     * @return 점유 성공 결과
     */
    public static LineRevisionClaim claimed(LineVisitDraftEntity source) {
        return new LineRevisionClaim(Status.CLAIMED, source);
    }

    /**
     * 다른 수정이 진행 중인 결과를 만든다.
     *
     * @return 다른 수정 처리 중 결과
     */
    public static LineRevisionClaim busy() {
        return new LineRevisionClaim(Status.BUSY, null);
    }

    /**
     * 기존 수정 의도를 더 이상 적용할 수 없는 결과를 만든다.
     *
     * @return 일반 신규 입력으로 바꾸면 안 되는 무효 결과
     */
    public static LineRevisionClaim invalid() {
        return new LineRevisionClaim(Status.INVALID, null);
    }
}
