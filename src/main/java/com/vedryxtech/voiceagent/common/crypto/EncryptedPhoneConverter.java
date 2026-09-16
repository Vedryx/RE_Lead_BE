package com.vedryxtech.voiceagent.common.crypto;

import org.springframework.data.mongodb.core.convert.MongoConversionContext;
import org.springframework.data.mongodb.core.convert.MongoValueConverter;
import org.springframework.stereotype.Component;

/**
 * Encrypts a phone property on the way into Mongo and decrypts it on the way out.
 *
 * <p>Applied with {@code @ValueConverter(EncryptedPhoneConverter.class)} on the field,
 * so the rest of the service keeps handling ordinary numbers and never has to remember
 * to encrypt. Spring Data resolves this as a bean — see
 * {@code MongoConfig#mongoCustomConversions} — which is what lets it hold the cipher.</p>
 *
 * <p>Queries are converted too: a derived query such as {@code findByCallingPhone}
 * passes its argument through {@link #write} before it reaches Mongo, so the stored
 * ciphertext is matched without the caller doing anything. A query built by hand
 * against a raw field name does <b>not</b> go through here — see
 * {@code LeadServiceImpl#applyPhoneCriteria}.</p>
 */
@Component
public class EncryptedPhoneConverter implements MongoValueConverter<String, String> {

    private final PhoneCipher cipher;

    public EncryptedPhoneConverter(PhoneCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String read(String stored, MongoConversionContext context) {
        return cipher.decrypt(stored);
    }

    @Override
    public String write(String plaintext, MongoConversionContext context) {
        return cipher.encrypt(plaintext);
    }
}
