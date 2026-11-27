package com.sponsorcheck.ingest;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Second entry point of the same app (DECISIONS.md D3): run one import, then exit.
 *
 * <pre>
 * java -jar app.jar --spring.main.web-application-type=none --sponsorcheck.import.source=GB
 * </pre>
 *
 * Railway's cron service runs exactly this command. Exit code 0 = new data is live, 1 = not.
 */
@Component
@ConditionalOnProperty("sponsorcheck.import.source")
public class ImportCommand implements ApplicationRunner, ExitCodeGenerator {

    private final RegisterImporter importer;
    private final List<RegisterSource> sources;
    private final String country;
    private int exitCode = 1;

    public ImportCommand(RegisterImporter importer, List<RegisterSource> sources,
                         @Value("${sponsorcheck.import.source}") String country) {
        this.importer = importer;
        this.sources = sources;
        this.country = country;
    }

    @Override
    public void run(ApplicationArguments args) {
        RegisterSource source = sources.stream()
                .filter(s -> s.country().equalsIgnoreCase(country))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No register source for " + country));
        ImportResult result = importer.run(source);
        exitCode = result.status() == ImportResult.Status.SUCCEEDED ? 0 : 1;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
