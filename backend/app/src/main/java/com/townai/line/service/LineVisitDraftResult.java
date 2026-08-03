package com.townai.line.service;

import com.townai.line.entity.LineVisitDraftEntity;

/**
 * LINE Text Message를 처리한 Draft 또는 사용자 안내이다.
 *
 * @param draft 표시할 Draft. 안내 결과이면 {@code null}
 * @param notice Draft를 만들지 않은 이유. Draft 결과이면 {@code null}
 */
public record LineVisitDraftResult(
        LineVisitDraftEntity draft,
        String notice
) {

    /**
     * 생성하거나 재사용한 Draft 결과를 만든다.
     *
     * @param draft 생성하거나 재사용한 Draft
     * @return Draft 결과
     */
    public static LineVisitDraftResult draft(LineVisitDraftEntity draft) {
        return new LineVisitDraftResult(
                java.util.Objects.requireNonNull(draft),
                null
        );
    }

    /**
     * Draft 대신 사용자에게 보낼 안내 결과를 만든다.
     *
     * @param notice 사용자에게 표시할 안내
     * @return 안내 결과
     */
    public static LineVisitDraftResult notice(String notice) {
        if (notice == null || notice.isBlank()) {
            throw new IllegalArgumentException("Notice must not be blank.");
        }
        return new LineVisitDraftResult(null, notice);
    }

    /**
     * 표시할 Draft가 있는지 확인한다.
     *
     * @return 표시할 Draft가 있으면 {@code true}
     */
    public boolean hasDraft() {
        return draft != null;
    }
}
