package com.sponsorcheck.ingest;

/**
 * The source no longer looks the way our parser expects (renamed column, missing file, ...).
 * The import is rejected and yesterday's data stays live (DECISIONS.md D4).
 */
public class SourceFormatException extends RuntimeException {

    public SourceFormatException(String message) {
        super(message);
    }
}
