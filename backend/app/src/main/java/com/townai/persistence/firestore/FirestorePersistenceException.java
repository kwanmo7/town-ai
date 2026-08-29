package com.townai.persistence.firestore;

/** Converts checked Firestore client failures into an application persistence failure. */
public class FirestorePersistenceException extends RuntimeException {

    public FirestorePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
