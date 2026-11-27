package com.sponsorcheck.ingest;

import com.sponsorcheck.matching.NameNormaliser;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one import: fetch, validate, then replace the country's rows in a single transaction.
 *
 * <p>Why one transaction is enough (DECISIONS.md D4): Postgres readers keep seeing the old rows
 * until the transaction commits, so the API never sees a half-loaded register. If anything fails,
 * the transaction rolls back and the old rows stay.
 */
@Service
public class RegisterImporter {

    /** A drop bigger than this versus the last good import is treated as a broken download. */
    static final double MAX_DROP = 0.20;

    private static final Logger log = LoggerFactory.getLogger(RegisterImporter.class);
    private static final int BATCH_SIZE = 1_000;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;   // only for batch inserts, which JdbcClient doesn't offer
    private final TransactionTemplate tx;

    public RegisterImporter(JdbcClient jdbc, JdbcTemplate jdbcTemplate, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.tx = tx;
    }

    public ImportResult run(RegisterSource source) {
        long runId = startRun(source.country());
        SourceSnapshot snapshot;
        try {
            snapshot = source.fetch();
        } catch (SourceFormatException e) {
            return finish(runId, null, new ImportResult(ImportResult.Status.REJECTED, 0, e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Import of {} failed", source.country(), e);
            return finish(runId, null, new ImportResult(ImportResult.Status.FAILED, 0, e.toString()));
        }

        Optional<String> problem = validate(source, snapshot);
        if (problem.isPresent()) {
            return finish(runId, snapshot,
                    new ImportResult(ImportResult.Status.REJECTED, snapshot.rows().size(), problem.get()));
        }

        int inserted = tx.execute(status -> replaceRows(source.country(), snapshot));
        return finish(runId, snapshot, new ImportResult(ImportResult.Status.SUCCEEDED, inserted, null));
    }

    private Optional<String> validate(RegisterSource source, SourceSnapshot snapshot) {
        int count = snapshot.rows().size();
        if (count < source.minimumRows()) {
            return Optional.of("Only %d rows, expected at least %d".formatted(count, source.minimumRows()));
        }
        Optional<Integer> lastGood = jdbc.sql("""
                        SELECT row_count FROM import_run
                        WHERE source = ? AND status = 'SUCCEEDED'
                        ORDER BY finished_at DESC LIMIT 1
                        """)
                .param(source.country())
                .query(Integer.class)
                .optional();
        if (lastGood.isPresent() && count < lastGood.get() * (1 - MAX_DROP)) {
            return Optional.of("Row count fell from %d to %d (more than %.0f%%)"
                    .formatted(lastGood.get(), count, MAX_DROP * 100));
        }
        return Optional.empty();
    }

    /** Deletes the country's old rows and inserts the new ones. Must run inside a transaction. */
    private int replaceRows(String country, SourceSnapshot snapshot) {
        jdbc.sql("DELETE FROM register_entry WHERE country = ?").param(country).update();

        // A "X t/a Y" line becomes two rows with the same legal_name, so a job post showing either name matches.
        List<Object[]> batch = new ArrayList<>();
        for (RegisterRow row : snapshot.rows()) {
            for (String name : NameNormaliser.splitTradingAs(row.legalName())) {
                batch.add(new Object[] {
                        country, row.legalName(), NameNormaliser.normalise(name), row.town(), row.route(),
                        row.rating(), row.sourceRef(), row.permitsIssued(), Date.valueOf(snapshot.dataAsOf())});
            }
        }
        for (int from = 0; from < batch.size(); from += BATCH_SIZE) {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO register_entry (country, legal_name, normalised_name, town, route,
                                                rating, source_ref, permits_issued, data_as_of)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, batch.subList(from, Math.min(from + BATCH_SIZE, batch.size())));
        }
        return batch.size();
    }

    private long startRun(String country) {
        return jdbc.sql("INSERT INTO import_run (source, status) VALUES (?, 'RUNNING') RETURNING id")
                .param(country)
                .query(Long.class)
                .single();
    }

    /** Records the outcome. row_count is the number of source lines, the figure the next run compares against. */
    private ImportResult finish(long runId, SourceSnapshot snapshot, ImportResult result) {
        jdbc.sql("""
                        UPDATE import_run
                        SET finished_at = now(), status = ?, row_count = ?, source_url = ?, message = ?
                        WHERE id = ?
                        """)
                .params(result.status().name(),
                        snapshot == null ? null : snapshot.rows().size(),
                        snapshot == null ? null : snapshot.sourceUrl().toString(),
                        result.message(),
                        runId)
                .update();
        log.info("Import run {}: {} ({} rows) {}", runId, result.status(), result.rowCount(),
                result.message() == null ? "" : result.message());
        return result;
    }
}
