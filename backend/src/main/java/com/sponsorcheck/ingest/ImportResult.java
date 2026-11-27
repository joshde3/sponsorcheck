package com.sponsorcheck.ingest;

public record ImportResult(Status status, int rowCount, String message) {

    public enum Status {
        /** New data is live. */
        SUCCEEDED,
        /** Download worked but failed validation. Old data is still live. */
        REJECTED,
        /** Download or parsing crashed. Old data is still live. */
        FAILED
    }
}
