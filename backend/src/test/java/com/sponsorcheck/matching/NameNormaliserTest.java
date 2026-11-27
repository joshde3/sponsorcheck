package com.sponsorcheck.matching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NameNormaliserTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', textBlock = """
            Monzo Bank Limited                  | monzo bank
            MONZO BANK LTD.                     | monzo bank
            Acme Holdings PLC                   | acme holdings
            Smith & Jones LLP                   | smith and jones
            The Ångström Café Ltd               | angstrom cafe
            Booking.com B.V.                    | bookingcom
            ASML Netherlands BV                 | asml netherlands
            Stripe Payments Europe DAC          | stripe payments europe
            Acme Widgets Public Limited Company | acme widgets
            Acme UK Ltd PLC                     | acme uk
            Siemens AG                          | siemens
            Infosys Pvt Ltd                     | infosys
            The Limited                         | limited
            Limited                             | limited
            '  Deloitte   LLP  '                | deloitte
            O'Neill-Ryan (Dublin) Ltd           | o neill ryan dublin
            """)
    void normalises(String input, String expected) {
        assertThat(NameNormaliser.normalise(input)).isEqualTo(expected);
    }

    @Test
    void blankBecomesEmpty() {
        assertThat(NameNormaliser.normalise("   ")).isEmpty();
        assertThat(NameNormaliser.normalise(null)).isEmpty();
    }

    @Test
    void splitsTradingAsNames() {
        assertThat(NameNormaliser.splitTradingAs("Acme Holdings Ltd t/a Acme Foods"))
                .containsExactly("Acme Holdings Ltd", "Acme Foods");
        assertThat(NameNormaliser.splitTradingAs("Bloggs Ltd T/A Joe's Cafe"))
                .containsExactly("Bloggs Ltd", "Joe's Cafe");
        assertThat(NameNormaliser.splitTradingAs("Smith Trading As Smith Cars"))
                .containsExactly("Smith", "Smith Cars");
        assertThat(NameNormaliser.splitTradingAs("Monzo Bank Limited"))
                .containsExactly("Monzo Bank Limited");
    }
}
