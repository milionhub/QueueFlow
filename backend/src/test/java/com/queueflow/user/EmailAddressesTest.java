package com.queueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class EmailAddressesTest {

    @Test
    void trimsAndLowerCases() {
        assertThat(EmailAddresses.normalize("  Juan.Example@Email.COM  ")).isEqualTo("juan.example@email.com");
        assertThat(EmailAddresses.normalize("\tada@example.com\n")).isEqualTo("ada@example.com");
    }

    @Test
    void isIdempotent() {
        String once = EmailAddresses.normalize(" Ada@Example.COM ");

        assertThat(EmailAddresses.normalize(once)).isEqualTo(once);
    }

    /** Under a Turkish default locale, "I".toLowerCase() would be a dotless "ı". */
    @Test
    void ignoresTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(EmailAddresses.normalize("INFO@QUEUEFLOW.IO")).isEqualTo("info@queueflow.io");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void acceptsPlausibleNormalizedAddresses() {
        assertThat(EmailAddresses.isValid("ada@example.com")).isTrue();
        assertThat(EmailAddresses.isValid("first.last+tag@mail.example.co.uk")).isTrue();
    }

    @Test
    void rejectsImplausibleAddresses() {
        for (String invalid : new String[] {"", "ada", "ada@", "@example.com", "ada@example", "ada@@example.com",
                "a da@example.com", "ada@example..com", "ada@.example.com"}) {
            assertThat(EmailAddresses.isValid(invalid)).as(invalid).isFalse();
        }
        assertThat(EmailAddresses.isValid(null)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> EmailAddresses.normalize(null));
    }
}
