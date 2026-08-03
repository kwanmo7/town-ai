-- Draft 확인 화면에서 수정 버튼을 누른 뒤 다음 Text Message로 값을 보정할 수
-- 있도록 수정 대기와 대체 완료 상태를 추가한다.
ALTER TABLE `line_visit_draft`
    DROP CHECK `CHK_LINE_VISIT_DRAFT_STATUS`;

ALTER TABLE `line_visit_draft`
    ADD CONSTRAINT `CHK_LINE_VISIT_DRAFT_STATUS`
        CHECK (
            `status` IN (
                'NEEDS_INPUT',
                'AWAITING_CONFIRMATION',
                'AWAITING_REVISION',
                'SUPERSEDED',
                'CONFIRMED',
                'CANCELLED',
                'EXPIRED'
            )
        ),
    ADD INDEX `IDX_LINE_VISIT_DRAFT_USER_STATUS_UPDATED`
        (`line_user_id`, `status`, `updated_at`, `id`);
