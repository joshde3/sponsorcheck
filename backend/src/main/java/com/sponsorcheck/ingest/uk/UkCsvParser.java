package com.sponsorcheck.ingest.uk;

import com.sponsorcheck.ingest.RegisterRow;
import com.sponsorcheck.ingest.SourceFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/** Parses the UK register CSV. The header is checked exactly, so a renamed column stops the import. */
final class UkCsvParser {

    /** Pinned header. If GOV.UK changes it, the import is rejected and you get an alert (D4, D6). */
    static final List<String> EXPECTED_HEADER =
            List.of("Organisation Name", "Town/City", "County", "Type & Rating", "Route");

    private UkCsvParser() {
    }

    static List<RegisterRow> parse(byte[] csv) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()                 // first line is the header
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(withoutBom(csv)), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            if (!parser.getHeaderNames().equals(EXPECTED_HEADER)) {
                throw new SourceFormatException("UK CSV header changed: " + parser.getHeaderNames());
            }
            List<RegisterRow> rows = new ArrayList<>();
            for (CSVRecord r : parser) {
                String name = r.get("Organisation Name");
                if (name.isEmpty()) {
                    continue;
                }
                rows.add(new RegisterRow(name, emptyToNull(r.get("Town/City")), emptyToNull(r.get("Route")),
                        emptyToNull(r.get("Type & Rating")), null, null));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Some Excel-made CSVs start with a UTF-8 byte-order mark, which would end up inside the first column name. */
    private static byte[] withoutBom(byte[] bytes) {
        boolean hasBom = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
        return hasBom ? java.util.Arrays.copyOfRange(bytes, 3, bytes.length) : bytes;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
