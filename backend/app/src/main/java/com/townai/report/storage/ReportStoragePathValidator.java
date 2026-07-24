package com.townai.report.storage;

import java.util.Arrays;

/**
 * 모든 ReportStorage 구현이 같은 논리 객체 경로 계약을 사용하도록 검증한다.
 */
final class ReportStoragePathValidator {

    static final String REPORTS_PREFIX = "reports/";

    private ReportStoragePathValidator() {
    }

    static String validate(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw invalid("Report storage path is empty.");
        }

        String objectName = storagePath.replace('\\', '/');
        String[] segments = objectName.split("/", -1);
        boolean invalidSegment = Arrays.stream(segments)
                .anyMatch(segment ->
                        segment.isEmpty()
                                || segment.equals(".")
                                || segment.equals("..")
                );
        boolean hasControlCharacter = objectName.codePoints()
                .anyMatch(Character::isISOControl);
        if (!objectName.startsWith(REPORTS_PREFIX)
                || invalidSegment
                || hasControlCharacter) {
            throw invalid("Report storage path is invalid.");
        }
        return objectName;
    }

    static void requireContent(String content) {
        if (content == null) {
            throw new ReportStorageException(
                    "Report content is null.",
                    new IllegalArgumentException("content")
            );
        }
    }

    private static ReportStorageException invalid(String message) {
        return new ReportStorageException(
                message,
                new IllegalArgumentException("storagePath")
        );
    }
}
