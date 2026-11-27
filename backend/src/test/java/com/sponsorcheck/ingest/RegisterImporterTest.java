package com.sponsorcheck.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sponsorcheck.TestcontainersConfig;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfig.class)
class RegisterImporterTest {

    @Autowired
    RegisterImporter importer;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.sql("DELETE FROM register_entry").update();
        jdbc.sql("DELETE FROM import_run").update();
    }

    @Test
    void loadsRowsAndSplitsTradingAsNames() {
        ImportResult result = importer.run(source(List.of(
                row("Monzo Bank Limited"), row("Acme Holdings Ltd t/a Acme Foods"))));

        assertThat(result.status()).isEqualTo(ImportResult.Status.SUCCEEDED);
        assertThat(names()).containsExactlyInAnyOrder("monzo bank", "acme holdings", "acme foods");
        assertThat(lastRunStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void replacesPreviousImportOfSameCountry() {
        importer.run(source(List.of(row("Old Company Ltd"))));
        importer.run(source(List.of(row("New Company Ltd"))));

        assertThat(names()).containsExactly("new company");
    }

    @Test
    void rejectsLargeDropAndKeepsOldData() {
        importer.run(source(rows(100)));

        ImportResult result = importer.run(source(rows(50)));   // half the rows vanished: broken download

        assertThat(result.status()).isEqualTo(ImportResult.Status.REJECTED);
        assertThat(result.message()).contains("fell from 100 to 50");
        assertThat(countRows()).isEqualTo(100);
    }

    @Test
    void rejectsChangedFormatAndKeepsOldData() {
        importer.run(source(List.of(row("Monzo Bank Limited"))));

        ImportResult result = importer.run(failingSource(new SourceFormatException("UK CSV header changed")));

        assertThat(result.status()).isEqualTo(ImportResult.Status.REJECTED);
        assertThat(names()).containsExactly("monzo bank");
        assertThat(lastRunStatus()).isEqualTo("REJECTED");
    }

    @Test
    void recordsCrashAsFailedAndKeepsOldData() {
        importer.run(source(List.of(row("Monzo Bank Limited"))));

        ImportResult result = importer.run(failingSource(new IllegalStateException("connection reset")));

        assertThat(result.status()).isEqualTo(ImportResult.Status.FAILED);
        assertThat(names()).containsExactly("monzo bank");
    }

    // --- helpers ---

    static RegisterRow row(String name) {
        return new RegisterRow(name, "London", "Skilled Worker", "Worker (A rating)", null, null);
    }

    static List<RegisterRow> rows(int n) {
        return IntStream.range(0, n).mapToObj(i -> row("Company " + i + " Ltd")).toList();
    }

    static RegisterSource source(List<RegisterRow> rows) {
        return new FakeSource(() -> new SourceSnapshot(URI.create("https://example.org/register.csv"),
                LocalDate.of(2026, 11, 27), rows));
    }

    static RegisterSource failingSource(RuntimeException e) {
        return new FakeSource(() -> {
            throw e;
        });
    }

    record FakeSource(java.util.function.Supplier<SourceSnapshot> supplier) implements RegisterSource {
        public String country() {
            return "GB";
        }

        public int minimumRows() {
            return 1;
        }

        public SourceSnapshot fetch() {
            return supplier.get();
        }
    }

    List<String> names() {
        return jdbc.sql("SELECT normalised_name FROM register_entry").query(String.class).list();
    }

    long countRows() {
        return jdbc.sql("SELECT count(*) FROM register_entry").query(Long.class).single();
    }

    String lastRunStatus() {
        return jdbc.sql("SELECT status FROM import_run ORDER BY id DESC LIMIT 1").query(String.class).single();
    }
}
