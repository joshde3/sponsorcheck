package com.sponsorcheck.ingest.uk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sponsorcheck.ingest.SourceFormatException;
import com.sponsorcheck.ingest.SourceSnapshot;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Uses a trimmed copy of the real Content API response from 27 Nov 2026; no network. */
class UkSponsorRegisterSourceTest {

    static final String CSV_URL = "https://assets.publishing.service.gov.uk/media/6b095b82c29db7468056c17e/"
            + "SP_-_Worker_and_Temporary_Worker_Web_Register_-_2026-11-27.csv";

    @Test
    void findsCurrentCsvThroughContentApiThenParsesIt() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://www.gov.uk" + UkSponsorRegisterSource.PUBLICATION_PATH))
                .andRespond(withSuccess(new ClassPathResource("uk/content-api.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(CSV_URL))
                .andRespond(withSuccess(UkCsvParserTest.sample(), MediaType.parseMediaType("text/csv")));

        SourceSnapshot snapshot = new UkSponsorRegisterSource(builder, "https://www.gov.uk").fetch();

        assertThat(snapshot.sourceUrl()).isEqualTo(URI.create(CSV_URL));
        assertThat(snapshot.dataAsOf()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(snapshot.rows()).hasSize(5);
        server.verify();
    }

    @Test
    void rejectsPublicationWithoutExactlyOneCsv() {
        var noCsv = new UkSponsorRegisterSource.ContentItem(null,
                new UkSponsorRegisterSource.Details(java.util.List.of(
                        new UkSponsorRegisterSource.Attachment("application/pdf", "https://example.org/a.pdf", "PDF"))));

        assertThatThrownBy(() -> UkSponsorRegisterSource.findCsvUrl(noCsv))
                .isInstanceOf(SourceFormatException.class)
                .hasMessageContaining("found 0");
    }
}
