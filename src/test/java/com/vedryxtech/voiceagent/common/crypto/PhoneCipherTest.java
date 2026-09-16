package com.vedryxtech.voiceagent.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneCipherTest {

    private static final String KEY = "dGVzdC1vbmx5LXBob25lLWtleS0zMi1ieXRlcyEhISE=";

    private static PhoneCipher cipher(String key) {
        CryptoProperties properties = new CryptoProperties();
        properties.setPhoneKey(key);
        return new PhoneCipher(properties);
    }

    @Test
    void a_number_round_trips() {
        PhoneCipher cipher = cipher(KEY);
        String stored = cipher.encrypt("+919876543210");

        assertThat(stored).startsWith("enc:v1:").doesNotContain("9876543210");
        assertThat(cipher.decrypt(stored)).isEqualTo("+919876543210");
    }

    @Test
    void the_same_number_always_encrypts_the_same_way_so_lookups_still_match() {
        PhoneCipher cipher = cipher(KEY);

        assertThat(cipher.encrypt("+919876543210")).isEqualTo(cipher.encrypt("+919876543210"));
        assertThat(cipher.encrypt("+919876543210")).isNotEqualTo(cipher.encrypt("+919876543211"));
    }

    @Test
    void matches_the_migration_script_byte_for_byte() {
        // Produced by scripts/encrypt-phones.js with the same key. If this drifts, the
        // migrated rows stop matching every lookup the application encrypts.
        assertThat(cipher(KEY).encrypt("+919876543210"))
                .isEqualTo("enc:v1:7JfHE9zdzZFzZU9qcTZkIAubSctXZ7EQjB/miLrWH5yne7DAKgOdC1w=");
    }

    @Test
    void an_encrypted_value_is_not_encrypted_twice() {
        PhoneCipher cipher = cipher(KEY);
        String once = cipher.encrypt("+919876543210");

        assertThat(cipher.encrypt(once)).isEqualTo(once);
    }

    @Test
    void null_blank_and_unmigrated_plaintext_pass_through() {
        PhoneCipher cipher = cipher(KEY);

        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("")).isEmpty();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("+919876543210")).isEqualTo("+919876543210");
    }

    @Test
    void without_a_key_numbers_stay_plaintext_but_ciphertext_is_refused() {
        PhoneCipher off = cipher("");
        String stored = cipher(KEY).encrypt("+919876543210");

        assertThat(off.isEnabled()).isFalse();
        assertThat(off.encrypt("+919876543210")).isEqualTo("+919876543210");
        // Handing ciphertext to the dialler as if it were a number is the failure to avoid.
        assertThatThrownBy(() -> off.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void the_wrong_key_fails_loudly() {
        String stored = cipher(KEY).encrypt("+919876543210");
        PhoneCipher other = cipher("oCpKoUbnndZ/LS8FDjfDJvs0QZ8BV+xNkxFzyRhyth8=");

        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_key_that_is_not_32_bytes_is_rejected_at_startup() {
        assertThatThrownBy(() -> cipher("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
    }
}
