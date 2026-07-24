package com.townai.line.processing;

import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 확인·취소 Postback Data를 내부 Draft 명령으로 변환한다.
 */
@Component
public class LinePostbackCommandParser {

    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "^action=(confirm|cancel)&draftId=([1-9][0-9]*)$"
    );

    /**
     * LINE Postback 명령 Parser를 생성한다.
     */
    public LinePostbackCommandParser() {
    }

    /**
     * 지원하는 Postback Data를 파싱한다.
     *
     * @param postbackData LINE Webhook Postback Data
     * @return 확인 또는 취소 Draft 명령
     * @throws LineEventHandlingException 형식 또는 ID가 올바르지 않은 경우
     */
    public LineDraftCommand parse(String postbackData) {
        if (postbackData == null) {
            throw invalidPostback();
        }
        Matcher matcher = COMMAND_PATTERN.matcher(postbackData);
        if (!matcher.matches()) {
            throw invalidPostback();
        }
        try {
            LineDraftAction action = "confirm".equals(matcher.group(1))
                    ? LineDraftAction.CONFIRM
                    : LineDraftAction.CANCEL;
            return new LineDraftCommand(
                    action,
                    Long.valueOf(matcher.group(2))
            );
        } catch (NumberFormatException exception) {
            throw invalidPostback();
        }
    }

    private LineEventHandlingException invalidPostback() {
        return new LineEventHandlingException(
                "INVALID_POSTBACK_DATA",
                false,
                null
        );
    }
}
