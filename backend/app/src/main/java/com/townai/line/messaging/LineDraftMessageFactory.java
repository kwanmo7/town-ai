package com.townai.line.messaging;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 저장된 LINE Visit Draft를 사용자 확인용 Push Message로 변환한다.
 */
@Component
public class LineDraftMessageFactory {

    private static final int MAX_TEXT_CODE_POINTS = 4500;

    /**
     * LINE Visit Draft 메시지 Factory를 생성한다.
     */
    public LineDraftMessageFactory() {
    }

    /**
     * Draft 값과 경고를 보여주고, 확인 가능할 때만 확인·취소 버튼을 추가한다.
     *
     * @param draft DB에 저장된 LINE Visit Draft
     * @return LINE Push Message API 요청
     */
    public LinePushRequest create(LineVisitDraftEntity draft) {
        List<Object> messages = new ArrayList<>();
        messages.add(LinePushRequest.TextMessage.of(
                truncate(createDraftText(draft))
        ));
        if (draft.getStatus()
                == LineVisitDraftStatus.AWAITING_CONFIRMATION) {
            messages.add(createConfirmationMessage(draft.getId()));
        }
        return new LinePushRequest(
                draft.getLineUserId(),
                messages
        );
    }

    private String createDraftText(LineVisitDraftEntity draft) {
        StringBuilder text = new StringBuilder()
                .append("[방문 기록 초안]\n")
                .append("지역: ")
                .append(draft.getArea() == null
                        ? "미확정"
                        : draft.getArea().getName())
                .append("\n방문일: ")
                .append(value(draft.getVisitDate()))
                .append("\n분위기: ")
                .append(score(draft.getAtmosphereScore()))
                .append("\n생활 인프라: ")
                .append(score(draft.getInfraScore()))
                .append("\n청결도: ")
                .append(score(draft.getCleanScore()))
                .append("\n넓은 집 가능성: ")
                .append(score(draft.getSizeScore()))
                .append("\n접근성: ")
                .append(score(draft.getAccessScore()))
                .append("\n메모: ")
                .append(value(draft.getMemo()));

        if (!draft.getWarnings().isEmpty()) {
            text.append("\n\n[확인사항]");
            draft.getWarnings().forEach(warning ->
                    text.append("\n- ").append(warning));
        }
        if (draft.getStatus() == LineVisitDraftStatus.NEEDS_INPUT) {
            text.append(
                    "\n\n누락되거나 모호한 내용을 보완해 자연어 평가를 다시 보내주세요."
            );
        }
        return text.toString();
    }

    private LinePushRequest.TemplateMessage createConfirmationMessage(
            Long draftId
    ) {
        List<LinePushRequest.PostbackAction> actions = List.of(
                LinePushRequest.PostbackAction.of(
                        "저장",
                        "action=confirm&draftId=" + draftId
                ),
                LinePushRequest.PostbackAction.of(
                        "취소",
                        "action=cancel&draftId=" + draftId
                )
        );
        return LinePushRequest.TemplateMessage.confirm(
                "방문 기록 저장 확인",
                LinePushRequest.ConfirmTemplate.of(
                        "이 초안을 방문 기록으로 저장할까요?",
                        actions
                )
        );
    }

    private String score(Integer value) {
        return value == null ? "미입력" : value + "/10";
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank()
                ? "없음"
                : value.toString();
    }

    private String truncate(String text) {
        int codePointCount = text.codePointCount(0, text.length());
        if (codePointCount <= MAX_TEXT_CODE_POINTS) {
            return text;
        }
        int endIndex = text.offsetByCodePoints(
                0,
                MAX_TEXT_CODE_POINTS - 3
        );
        return text.substring(0, endIndex) + "...";
    }
}
