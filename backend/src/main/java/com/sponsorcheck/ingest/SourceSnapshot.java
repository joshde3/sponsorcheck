package com.sponsorcheck.ingest;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** Everything one download of a register produced, before it is validated. */
public record SourceSnapshot(URI sourceUrl, LocalDate dataAsOf, List<RegisterRow> rows) {
}
