package com.townai.persistence.firestore;

/** Firestore collection names are centralized to keep documents and scripts aligned. */
public final class FirestoreCollections {

    public static final String AREAS = "areas";
    public static final String VISITS = "visits";
    public static final String REPORTS = "reports";
    public static final String LINE_VISIT_DRAFTS = "lineVisitDrafts";
    public static final String LINE_WEBHOOK_EVENTS = "lineWebhookEvents";
    public static final String COUNTERS = "counters";
    public static final String AREA_KEYS = "areaKeys";

    private FirestoreCollections() {
    }
}
