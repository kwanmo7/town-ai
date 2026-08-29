package com.townai.persistence.firestore;

/** Raised when an application-level Firestore unique key is already reserved. */
public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(String message) {
        super(message);
    }
}
