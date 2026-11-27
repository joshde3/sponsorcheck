package com.sponsorcheck.ingest;

/** One public register (UK, NL, IE). Each country implements how to download and parse its own file. */
public interface RegisterSource {

    /** ISO country code stored in register_entry.country, e.g. "GB". */
    String country();

    /**
     * Fewer rows than this means the download is broken, not that sponsors vanished.
     * Set well below the real size (UK has well over 100k lines).
     */
    int minimumRows();

    /** Downloads and parses the register. Throws {@link SourceFormatException} if the format changed. */
    SourceSnapshot fetch();
}
