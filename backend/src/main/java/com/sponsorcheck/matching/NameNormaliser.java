package com.sponsorcheck.matching;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a company name into a comparable key, the same way for register rows (at import time)
 * and for names found on job posts (at query time). See DECISIONS.md D7.
 *
 * <p>Example: {@code "The Ångström Café & Co. Ltd."} becomes {@code "angstrom cafe and co"}.
 */
public final class NameNormaliser {

    /** Legal-form words removed from the END of a name. Multi-word forms are listed as phrases. */
    private static final List<List<String>> LEGAL_SUFFIXES = phrases(
            // UK
            "limited liability partnership", "public limited company", "community interest company",
            "limited", "ltd", "plc", "llp", "lp", "cic",
            // Netherlands
            "bv", "nv", "vof",
            // Ireland
            "designated activity company", "company limited by guarantee", "unlimited company",
            "dac", "clg", "uc", "teoranta", "teo",
            // International forms that appear on job sites
            "private limited", "pvt", "gmbh", "ag", "inc", "incorporated", "llc", "corp", "corporation", "sa", "sarl");

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern TRADING_AS = Pattern.compile("(?i)\\s+(?:t/a|trading as)\\s+");

    private NameNormaliser() {
    }

    /**
     * Splits "Acme Holdings Ltd t/a Acme Foods" into both names, because a job post may show either.
     * A name without "t/a" or "trading as" comes back as a one-element list.
     */
    public static List<String> splitTradingAs(String name) {
        return Arrays.stream(TRADING_AS.split(name.strip()))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    public static String normalise(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String s = Normalizer.normalize(name, Normalizer.Form.NFKD);   // "é" becomes "e" + accent mark
        s = ACCENTS.matcher(s).replaceAll("");                         // drop the accent marks
        s = s.toLowerCase(Locale.ROOT);
        s = s.replace("&", " and ");
        s = s.replace(".", "");                                        // "b.v." -> "bv", "ltd." -> "ltd"
        s = NOT_LETTER_OR_DIGIT.matcher(s).replaceAll(" ").strip();    // other punctuation becomes a space

        List<String> words = new ArrayList<>(List.of(s.split(" ")));
        if (words.size() > 1 && words.getFirst().equals("the")) {
            words.removeFirst();
        }
        stripLegalSuffixes(words);
        return String.join(" ", words);
    }

    /** Removes legal forms from the end, repeatedly ("acme uk ltd plc" -> "acme uk"), but never the whole name. */
    private static void stripLegalSuffixes(List<String> words) {
        boolean removed = true;
        while (removed) {
            removed = false;
            for (List<String> suffix : LEGAL_SUFFIXES) {
                int start = words.size() - suffix.size();
                if (start >= 1 && words.subList(start, words.size()).equals(suffix)) {
                    words.subList(start, words.size()).clear();
                    removed = true;
                    break;
                }
            }
        }
    }

    /** Longest phrases first, so "public limited company" wins over "company". */
    private static List<List<String>> phrases(String... suffixes) {
        return Arrays.stream(suffixes)
                .map(phrase -> List.of(phrase.split(" ")))
                .sorted((a, b) -> b.size() - a.size())
                .toList();
    }
}
