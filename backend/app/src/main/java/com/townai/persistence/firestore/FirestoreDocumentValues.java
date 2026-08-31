package com.townai.persistence.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 명시적으로 정의한 Firestore 문서 Schema를 Java 타입으로 안전하게 변환한다. */
public final class FirestoreDocumentValues {

    private FirestoreDocumentValues() {
    }

    /**
     * Firestore Timestamp 필드를 nullable UTC Instant로 변환한다.
     *
     * @param document 값을 읽을 Firestore 문서
     * @param field Timestamp 필드 이름
     * @return 변환한 Instant 또는 필드가 없을 때 {@code null}
     */
    public static Instant instant(DocumentSnapshot document, String field) {
        Timestamp timestamp = document.getTimestamp(field);
        return timestamp == null
                ? null
                : Instant.ofEpochSecond(
                        timestamp.getSeconds(),
                        timestamp.getNanos()
                );
    }

    /**
     * nullable UTC Instant를 Firestore Timestamp 값으로 변환한다.
     *
     * @param instant 변환할 Instant
     * @return Firestore Timestamp 또는 입력값이 없을 때 {@code null}
     */
    public static Timestamp timestamp(Instant instant) {
        return instant == null
                ? null
                : Timestamp.ofTimeSecondsAndNanos(
                        instant.getEpochSecond(),
                        instant.getNano()
                );
    }

    /**
     * ISO 날짜 문자열 필드를 nullable LocalDate로 변환한다.
     *
     * @param document 값을 읽을 Firestore 문서
     * @param field ISO 날짜 문자열 필드 이름
     * @return 변환한 날짜 또는 필드가 없을 때 {@code null}
     */
    public static LocalDate localDate(DocumentSnapshot document, String field) {
        String value = document.getString(field);
        return value == null ? null : LocalDate.parse(value);
    }

    /**
     * nullable LocalDate를 ISO 날짜 문자열로 변환한다.
     *
     * @param value 변환할 날짜
     * @return ISO 날짜 문자열 또는 입력값이 없을 때 {@code null}
     */
    public static String localDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    /**
     * 숫자 필드를 int로 변환하며 값이 없으면 0을 반환한다.
     *
     * @param document 값을 읽을 Firestore 문서
     * @param field 숫자 필드 이름
     * @return 변환한 정수 또는 필드가 없을 때 0
     */
    public static int integer(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value == null ? 0 : Math.toIntExact(value);
    }

    /**
     * 숫자 필드를 nullable Integer로 변환한다.
     *
     * @param document 값을 읽을 Firestore 문서
     * @param field 숫자 필드 이름
     * @return 변환한 정수 또는 필드가 없을 때 {@code null}
     */
    public static Integer nullableInteger(
            DocumentSnapshot document,
            String field
    ) {
        Long value = document.getLong(field);
        return value == null ? null : Math.toIntExact(value);
    }

    /**
     * 배열 필드에서 문자열 값만 안전하게 추출한다.
     *
     * @param document 값을 읽을 Firestore 문서
     * @param field 배열 필드 이름
     * @return 불변 문자열 목록
     */
    public static List<String> strings(
            DocumentSnapshot document,
            String field
    ) {
        Object raw = document.get(field);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String value) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 배열 필드에서 숫자 값을 Long으로 변환해 추출한다.
     *
     * @param document 값을 읽을 Firestore 문서
     * @param field 배열 필드 이름
     * @return 불변 숫자 목록
     */
    public static List<Long> longs(
            DocumentSnapshot document,
            String field
    ) {
        Object raw = document.get(field);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.longValue());
            }
        }
        return List.copyOf(result);
    }
}
