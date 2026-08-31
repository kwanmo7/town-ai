package com.townai.persistence.firestore;

/** Backend 문서와 운영 Script가 같은 이름을 사용하도록 Collection 이름을 한곳에서 관리한다. */
public final class FirestoreCollections {

    /** Area 문서 Collection이다. */
    public static final String AREAS = "areas";
    /** Visit 문서 Collection이다. */
    public static final String VISITS = "visits";
    /** Report Metadata 문서 Collection이다. */
    public static final String REPORTS = "reports";
    /** LINE Visit Draft 문서 Collection이다. */
    public static final String LINE_VISIT_DRAFTS = "lineVisitDrafts";
    /** LINE Webhook 멱등성 문서 Collection이다. */
    public static final String LINE_WEBHOOK_EVENTS = "lineWebhookEvents";
    /** 숫자 ID Counter 문서 Collection이다. */
    public static final String COUNTERS = "counters";
    /** Area 복합 고유 키 예약 문서 Collection이다. */
    public static final String AREA_KEYS = "areaKeys";

    private FirestoreCollections() {
    }
}
