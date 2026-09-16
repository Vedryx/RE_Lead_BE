package com.vedryxtech.voiceagent.common.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts a phone number so the same number always produces the same ciphertext.
 *
 * <p><b>Why deterministic.</b> Every phone lookup in this service is an equality
 * match: the {@code uk_calling_phone} unique index that stops a second lead being
 * created for one number, {@code findByCallingPhone}, and the {@code ?phone=}
 * search. Random-IV encryption gives a different ciphertext each write, which
 * breaks all three — the unique index stops seeing duplicates, and a search can
 * only be answered by decrypting every document in the collection. Deriving the
 * IV from the plaintext keeps equality working, so the indexes and queries are
 * untouched by encryption.</p>
 *
 * <p>The cost is that equal numbers are visibly equal in the stored data. That is
 * inherent to any scheme you can still search; it reveals which rows share a
 * number, never the number itself.</p>
 *
 * <p>The IV is {@code HMAC-SHA256(key, plaintext)} truncated to 12 bytes — the
 * synthetic-IV construction. Two different numbers get different IVs, so GCM's
 * requirement that a key/IV pair is never reused across distinct plaintexts holds.</p>
 */
@Component
public class PhoneCipher {

    private static final Logger log = LoggerFactory.getLogger(PhoneCipher.class);

    /** Marks a value this class wrote. Anything without it is read back untouched. */
    static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec macKey;
    private final boolean enabled;

    public PhoneCipher(CryptoProperties properties) {
        String configured = properties.getPhoneKey() == null ? "" : properties.getPhoneKey().trim();
        this.enabled = !configured.isEmpty();
        if (!enabled) {
            this.aesKey = null;
            this.macKey = null;
            log.warn("app.crypto.phone-key is not set — phone numbers are stored in plaintext. "
                    + "Set PHONE_ENC_KEY to turn encryption on.");
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(configured);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.crypto.phone-key must be base64", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "app.crypto.phone-key must decode to 32 bytes (AES-256), got " + raw.length);
        }
        this.aesKey = new SecretKeySpec(raw, "AES");
        // A separate key for the IV derivation. Using the same bytes for both the
        // cipher and the MAC is the kind of reuse that turns one weakness into two.
        this.macKey = new SecretKeySpec(hmac(raw, "phone-iv".getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        log.info("Phone numbers are encrypted at rest (AES-256-GCM, deterministic IV)");
    }

    /** True when a key is configured. False leaves every value in plaintext. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Encrypts one number. Null and blank pass through, and a value that is already
     * encrypted is returned unchanged so re-saving a document cannot double-encrypt it.
     */
    public String encrypt(String plaintext) {
        if (!enabled || plaintext == null || plaintext.isBlank() || isEncrypted(plaintext)) {
            return plaintext;
        }
        byte[] plain = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] iv = Arrays.copyOf(hmac(macKey.getEncoded(), plain), IV_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt a phone number", ex);
        }
    }

    /**
     * Decrypts one number. A value without the marker is returned as it is: the
     * database holds plaintext until the migration has run, and a read must not
     * fail on a row that has not been converted yet.
     */
    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        if (!enabled) {
            // The data is encrypted but the key is gone. Returning the ciphertext
            // would put it on screen and into the dialler as if it were a number.
            throw new IllegalStateException(
                    "Stored phone numbers are encrypted but app.crypto.phone-key is not set");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Stored phone number is not valid base64", ex);
        }
        if (payload.length <= IV_LENGTH) {
            throw new IllegalStateException("Stored phone number is too short to decrypt");
        }
        try {
            byte[] iv = Arrays.copyOf(payload, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt a phone number — wrong key?", ex);
        }
    }

    /** True when this value was written by {@link #encrypt}. */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not derive the phone IV", ex);
        }
    }
}
