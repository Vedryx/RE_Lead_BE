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
    void keepsNationalNumbersAsTheyAre() {
        assertThat(PhoneNumbers.normalize("1234567890")).isEqualTo("1234567890");
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
