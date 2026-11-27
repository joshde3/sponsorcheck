package com.sponsorcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SponsorCheckApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(SponsorCheckApplication.class, args);
        // In import mode there is no web server, so exit with the import's result (see ImportCommand).
        if (context.getEnvironment().containsProperty("sponsorcheck.import.source")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
