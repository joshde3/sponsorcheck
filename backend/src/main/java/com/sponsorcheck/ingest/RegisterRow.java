package com.sponsorcheck.ingest;

/**
 * One organisation line from a register, as the source printed it.
 * Fields a country doesn't have are null (see the columns in V1__registers.sql).
 */
public record RegisterRow(
        String legalName,
        String town,
        String route,
        String rating,
        String sourceRef,
        Integer permitsIssued) {
}
