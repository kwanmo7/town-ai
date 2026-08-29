package com.townai.persistence.firestore;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Typed conversion helpers for the deliberately explicit Firestore document schema. */
public final class FirestoreDocumentValues {

    private FirestoreDocumentValues() {
    }

    public static Instant instant(DocumentSnapshot document, String field) {
        Timestamp timestamp = document.getTimestamp(field);
        return timestamp == null
                ? null
                : Instant.ofEpochSecond(
                        timestamp.getSeconds(),
                        timestamp.getNanos()
                );
    }

    public static Timestamp timestamp(Instant instant) {
        return instant == null
                ? null
                : Timestamp.ofTimeSecondsAndNanos(
                        instant.getEpochSecond(),
                        instant.getNano()
                );
    }

    public static LocalDate localDate(DocumentSnapshot document, String field) {
        String value = document.getString(field);
        return value == null ? null : LocalDate.parse(value);
    }

    public static String localDate(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static int integer(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value == null ? 0 : Math.toIntExact(value);
    }

    public static Integer nullableInteger(
            DocumentSnapshot document,
            String field
    ) {
        Long value = document.getLong(field);
        return value == null ? null : Math.toIntExact(value);
    }

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
