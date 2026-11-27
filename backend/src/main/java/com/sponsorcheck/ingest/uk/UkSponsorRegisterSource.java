package com.sponsorcheck.ingest.uk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sponsorcheck.ingest.RegisterRow;
import com.sponsorcheck.ingest.RegisterSource;
import com.sponsorcheck.ingest.SourceFormatException;
import com.sponsorcheck.ingest.SourceSnapshot;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * UK Register of licensed sponsors (workers).
 *
 * <p>The CSV's URL changes with every update, so we first ask the GOV.UK Content API which file
 * is current (DECISIONS.md D1), then download and parse it.
 */
@Component
public class UkSponsorRegisterSource implements RegisterSource {

    static final String PUBLICATION_PATH = "/api/content/government/publications/register-of-licensed-sponsors-workers";

    private final RestClient http;

    public UkSponsorRegisterSource(RestClient.Builder builder,
                                   @Value("${sponsorcheck.uk.base-url:https://www.gov.uk}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    @Override
    public String country() {
        return "GB";
    }

    @Override
    public int minimumRows() {
        return 50_000;   // the real file has well over 100k lines
    }

    @Override
    public SourceSnapshot fetch() {
        ContentItem publication = http.get().uri(PUBLICATION_PATH).retrieve().body(ContentItem.class);
        URI csvUrl = findCsvUrl(publication);
        byte[] csv = http.get().uri(csvUrl).retrieve().body(byte[].class);
        List<RegisterRow> rows = UkCsvParser.parse(csv);
        LocalDate asOf = publication.publicUpdatedAt().toLocalDate();
        return new SourceSnapshot(csvUrl, asOf, rows);
    }

    /** The publication should have exactly one CSV attachment. Anything else means GOV.UK changed it. */
    static URI findCsvUrl(ContentItem publication) {
        if (publication == null || publication.details() == null || publication.details().attachments() == null) {
            throw new SourceFormatException("GOV.UK publication has no attachments");
        }
        List<Attachment> csvs = publication.details().attachments().stream()
                .filter(a -> "text/csv".equals(a.contentType()))
                .toList();
        if (csvs.size() != 1) {
            throw new SourceFormatException("Expected 1 CSV attachment, found " + csvs.size());
        }
        return URI.create(csvs.getFirst().url());
    }

    // The small part of the Content API JSON we read. Everything else in the response is ignored.

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentItem(@JsonProperty("public_updated_at") OffsetDateTime publicUpdatedAt, Details details) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Details(List<Attachment> attachments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Attachment(@JsonProperty("content_type") String contentType, String url, String title) {
    }
}
