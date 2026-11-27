package com.sponsorcheck.ingest.uk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sponsorcheck.ingest.RegisterRow;
import com.sponsorcheck.ingest.SourceFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class UkCsvParserTest {

    static byte[] sample() throws IOException {
        try (var in = UkCsvParserTest.class.getResourceAsStream("/uk/register-sample.csv")) {
            return in.readAllBytes();
        }
    }

    @Test
    void parsesEveryLineWithItsRouteAndRating() throws IOException {
        List<RegisterRow> rows = UkCsvParser.parse(sample());

        assertThat(rows).hasSize(5);
        assertThat(rows.getFirst())
                .isEqualTo(new RegisterRow("Monzo Bank Limited", "London", "Skilled Worker", "Worker (A rating)", null, null));
        assertThat(rows.get(4).legalName()).isEqualTo("Smith, Jones & Partners LLP");   // comma inside quotes
        assertThat(rows.get(3).legalName()).isEqualTo("The Ångström Café Ltd");         // UTF-8 kept
    }

    @Test
    void ignoresByteOrderMark() throws IOException {
        byte[] csv = sample();
        byte[] withBom = new byte[csv.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(csv, 0, withBom, 3, csv.length);

        assertThat(UkCsvParser.parse(withBom)).hasSize(5);
    }

    @Test
    void rejectsChangedHeader() {
        byte[] renamed = """
                "Organisation","Town/City","County","Type & Rating","Route"
                "Monzo Bank Limited","London","","Worker (A rating)","Skilled Worker"
                """.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> UkCsvParser.parse(renamed))
                .isInstanceOf(SourceFormatException.class)
                .hasMessageContaining("header changed");
    }
}
