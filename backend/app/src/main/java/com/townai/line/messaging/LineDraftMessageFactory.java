package com.townai.line.messaging;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.townai.line.messaging.LineFlexComponents.box;
import static com.townai.line.messaging.LineFlexComponents.button;
import static com.townai.line.messaging.LineFlexComponents.postback;
import static com.townai.line.messaging.LineFlexComponents.separator;
import static com.townai.line.messaging.LineFlexComponents.text;

/**
 * 저장된 LINE Visit Draft를 사용자 확인용 Flex Message로 변환한다.
 *
 * <p>Draft 값은 하나의 Bubble에서 보여주고, 확인 가능한 상태에만 저장·취소
 * Postback 버튼을 추가한다. 누락 Draft는 현재 처리 가능한 값과 경고를 보여준 뒤
 * 전체 자연어 평가를 다시 보내도록 안내한다.</p>
 */
@Component
public class LineDraftMessageFactory {

    private static final int MAX_COMPONENT_TEXT_CODE_POINTS = 1500;

    private static final String PRIMARY_COLOR = "#176B5B";
    private static final String PRIMARY_LIGHT_COLOR = "#D8F3EA";
    private static final String WARNING_COLOR = "#A66A00";
    private static final String WARNING_LIGHT_COLOR = "#FFF0D5";
    private static final String LABEL_COLOR = "#777777";
    private static final String TEXT_COLOR = "#333333";

    /**
     * LINE Visit Draft 메시지 Factory를 생성한다.
     */
    public LineDraftMessageFactory() {
    }

    /**
     * Draft 값과 경고를 하나의 Flex Bubble로 구성한다.
     *
     * @param draft DB에 저장된 LINE Visit Draft
     * @return LINE Push Message API 요청
     */
    public LinePushRequest create(LineVisitDraftEntity draft) {
        Map<String, Object> bubble = createBubble(draft);
        LineFlexMessage message = LineFlexMessage.of(
                createAltText(draft),
                bubble
        );
        return new LinePushRequest(
                draft.getLineUserId(),
                List.of(message)
        );
    }

    private Map<String, Object> createBubble(
            LineVisitDraftEntity draft
    ) {
        LineFlexObjectBuilder bubble = LineFlexObjectBuilder.type("bubble")
                .property("size", "mega")
                .property("direction", "ltr")
                .property("header", createHeader(draft))
                .property("body", createBody(draft));
        if (isConfirmable(draft)) {
            bubble.property("footer", createFooter(draft.getId()));
        }
        return bubble.build();
    }

    private Map<String, Object> createHeader(
            LineVisitDraftEntity draft
    ) {
        boolean needsInput = needsInput(draft);
        String backgroundColor = needsInput
                ? WARNING_COLOR
                : PRIMARY_COLOR;
        String subtitleColor = needsInput
                ? WARNING_LIGHT_COLOR
                : PRIMARY_LIGHT_COLOR;
        String title = needsInput
                ? "추가 입력이 필요합니다"
                : "방문 기록 초안";
        String subtitle = needsInput
                ? "확인한 값을 검토하고 전체 평가를 다시 보내주세요."
                : "내용을 확인한 후 저장해주세요.";

        return box("vertical", List.of(
                text(title)
                        .property("color", "#FFFFFF")
                        .property("size", "xl")
                        .property("weight", "bold")
                        .build(),
                text(subtitle)
                        .property("color", subtitleColor)
                        .property("size", "sm")
                        .property("margin", "sm")
                        .property("wrap", true)
                        .build()
        )).property("backgroundColor", backgroundColor)
                .property("paddingAll", "20px")
                .build();
    }

    private Map<String, Object> createBody(
            LineVisitDraftEntity draft
    ) {
        List<Object> contents = new ArrayList<>();
        contents.add(valueRow(
                "지역",
                draft.getArea() == null
                        ? "미확정"
                        : draft.getArea().getName(),
                true
        ));
        contents.add(valueRow(
                "방문일",
                draft.getVisitDate() == null
                        ? "미입력"
                        : draft.getVisitDate().toString(),
                false
        ));
        contents.add(separator("md"));
        contents.add(scoreRow("분위기", draft.getAtmosphereScore()));
        contents.add(scoreRow("생활 인프라", draft.getInfraScore()));
        contents.add(scoreRow("청결도", draft.getCleanScore()));
        contents.add(scoreRow(
                "넓은 집 가능성",
                draft.getSizeScore()
        ));
        contents.add(scoreRow("접근성", draft.getAccessScore()));
        contents.add(separator("md"));
        contents.add(text("메모")
                .property("color", LABEL_COLOR)
                .property("size", "sm")
                .property("weight", "bold")
                .build());
        contents.add(text(truncate(value(draft.getMemo())))
                .property("color", TEXT_COLOR)
                .property("size", "sm")
                .property("wrap", true)
                .build());
        appendWarnings(contents, draft.getWarnings());
        if (needsInput(draft)) {
            contents.add(text(
                    "누락되거나 모호한 내용을 포함해 전체 평가를 다시 보내주세요."
            ).property("color", WARNING_COLOR)
                    .property("size", "sm")
                    .property("weight", "bold")
                    .property("wrap", true)
                    .build());
        }

        return box("vertical", contents)
                .property("spacing", "md")
                .property("paddingAll", "20px")
                .build();
    }

    private void appendWarnings(
            List<Object> contents,
            List<String> warnings
    ) {
        if (warnings.isEmpty()) {
            return;
        }
        String warningText = warnings.stream()
                .map(warning -> "• " + warning)
                .collect(java.util.stream.Collectors.joining("\n"));
        contents.add(separator("md"));
        contents.add(text("확인사항")
                .property("color", WARNING_COLOR)
                .property("size", "sm")
                .property("weight", "bold")
                .build());
        contents.add(text(truncate(warningText))
                .property("color", TEXT_COLOR)
                .property("size", "sm")
                .property("wrap", true)
                .build());
    }

    private Map<String, Object> createFooter(Long draftId) {
        if (draftId == null || draftId <= 0) {
            throw new IllegalStateException(
                    "Persisted LINE Draft ID is required."
            );
        }
        return box("horizontal", List.of(
                button(
                        "primary",
                        PRIMARY_COLOR,
                        postback(
                                "저장",
                                "action=confirm&draftId=" + draftId,
                                "저장"
                        )
                ),
                button(
                        "secondary",
                        null,
                        postback(
                                "취소",
                                "action=cancel&draftId=" + draftId,
                                "취소"
                        )
                )
        )).property("spacing", "md")
                .property("paddingAll", "16px")
                .build();
    }

    private Map<String, Object> valueRow(
            String label,
            String value,
            boolean emphasized
    ) {
        LineFlexObjectBuilder valueText = text(value)
                .property("align", "end")
                .property("wrap", true)
                .property("flex", 4);
        if (emphasized) {
            valueText.property("weight", "bold");
        }
        return box("horizontal", List.of(
                text(label)
                        .property("color", LABEL_COLOR)
                        .property("size", "sm")
                        .property("flex", 2)
                        .build(),
                valueText.build()
        )).build();
    }

    private Map<String, Object> scoreRow(
            String label,
            Integer score
    ) {
        boolean missing = score == null;
        return box("horizontal", List.of(
                text(label)
                        .property("color", "#555555")
                        .property("flex", 4)
                        .build(),
                text(missing ? "미입력" : score + " / 10")
                        .property("weight", "bold")
                        .property("align", "end")
                        .property(
                                "color",
                                missing ? WARNING_COLOR : PRIMARY_COLOR
                        )
                        .property("flex", 2)
                        .build()
        )).build();
    }

    private String createAltText(LineVisitDraftEntity draft) {
        if (needsInput(draft)) {
            return "방문 기록에 추가 입력이 필요합니다.";
        }
        String areaName = draft.getArea() == null
                ? "방문 기록"
                : draft.getArea().getName();
        return areaName + " 방문 기록 초안을 확인해주세요.";
    }

    private boolean isConfirmable(LineVisitDraftEntity draft) {
        return draft.getStatus()
                == LineVisitDraftStatus.AWAITING_CONFIRMATION;
    }

    private boolean needsInput(LineVisitDraftEntity draft) {
        return draft.getStatus() == LineVisitDraftStatus.NEEDS_INPUT;
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }

    private String truncate(String value) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= MAX_COMPONENT_TEXT_CODE_POINTS) {
            return value;
        }
        int endIndex = value.offsetByCodePoints(
                0,
                MAX_COMPONENT_TEXT_CODE_POINTS - 3
        );
        return value.substring(0, endIndex) + "...";
    }
}
