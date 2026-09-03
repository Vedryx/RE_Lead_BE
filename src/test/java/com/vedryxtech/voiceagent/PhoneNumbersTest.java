package com.vedryxtech.voiceagent;

import com.vedryxtech.voiceagent.common.util.PhoneNumbers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void stripsFormattingButKeepsThePlusPrefix() {
        assertThat(PhoneNumbers.normalize("+91 98765 43210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("+91-987-654-3210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("(+91) 9876543210")).isEqualTo("+919876543210");
    }

    @Test
    void promotesBareTenDigitToE164WithDefaultCountry() {
        // M-11: pre-rework returned "1234567890" unchanged, so the same person paste-imported
        // as "9876543210" and later reached as "+919876543210" produced two leads. The 10-digit
        // form is now promoted to +91 so the unique index catches the duplicate.
        assertThat(PhoneNumbers.normalize("9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("+919876543210")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("9876543210"))
                .as("bare and prefixed forms collide on the unique index")
                .isEqualTo(PhoneNumbers.normalize("+919876543210"));
    }

    @Test
    void leavesLeadingZeroNumbersUnpromoted() {
        // Local trunk-prefixed numbers stay as-is; we do not want to guess a country.
        assertThat(PhoneNumbers.normalize("0987654321")).isEqualTo("0987654321");
    }

    @Test
    void rewritesTheInternationalPrefix() {
        assertThat(PhoneNumbers.normalize("00919876543210")).isEqualTo("+919876543210");
    }

    @Test
    void returnsNullForBlankInput() {
        assertThat(PhoneNumbers.normalize(null)).isNull();
        assertThat(PhoneNumbers.normalize("   ")).isNull();
        assertThat(PhoneNumbers.normalize("--")).isNull();
    }
}
