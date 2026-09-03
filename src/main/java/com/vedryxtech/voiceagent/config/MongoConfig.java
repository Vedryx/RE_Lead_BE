package com.vedryxtech.voiceagent.config;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

/**
 * Keeps the stored BSON identical in shape to the payloads the voice agent emits: every
 * {@link WireValue} enum is persisted as its wire value and timestamps as native BSON dates.
 *
 * <p>BSON has no offset-aware date type, so an incoming {@code +05:30} timestamp is stored as the
 * equivalent UTC instant and read back as UTC. The point in time is preserved.</p>
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                OffsetDateTimeToDateConverter.INSTANCE,
                DateToOffsetDateTimeConverter.INSTANCE,
                WireValueToStringConverter.INSTANCE,
                // Registered explicitly so legacy role names (orgAdmin, manager, ...) still
                // deserialise on read into the collapsed ADMIN/MEMBER pair. Without this, the
                // generic WireValue factory below throws on any docs written before the rework.
                StringToUserRoleConverter.INSTANCE,
                new StringToWireValueConverterFactory()));
    }

    /**
     * Drops the {@code _class} type hint. Each collection holds a single document type, so the
     * stored BSON stays the shape the voice agent emits.
     */
    @Bean
    public InitializingBean removeMongoTypeHints(MappingMongoConverter converter) {
        return () -> converter.setTypeMapper(new DefaultMongoTypeMapper(null));
    }

    @WritingConverter
    enum OffsetDateTimeToDateConverter implements Converter<OffsetDateTime, Date> {
        INSTANCE;

        @Override
        public Date convert(OffsetDateTime source) {
            return Date.from(source.toInstant());
        }
    }

    @ReadingConverter
    enum DateToOffsetDateTimeConverter implements Converter<Date, OffsetDateTime> {
        INSTANCE;

        @Override
        public OffsetDateTime convert(Date source) {
            return source.toInstant().atOffset(ZoneOffset.UTC);
        }
    }

    /** Legacy-tolerant reader for UserRole. Uses the enum's own {@code fromValue}. */
    @ReadingConverter
    enum StringToUserRoleConverter implements Converter<String, UserRole> {
        INSTANCE;

        @Override
        public UserRole convert(String source) {
            return UserRole.fromValue(source);
        }
    }

    /** Writes any {@link WireValue} enum as its wire value rather than {@code name()}. */
    @WritingConverter
    enum WireValueToStringConverter implements Converter<WireValue, String> {
        INSTANCE;

        @Override
        public String convert(WireValue source) {
            return source.getValue();
        }
    }

    /**
     * Reads a stored wire value back into whichever {@link WireValue} enum the field declares.
     * A factory rather than one converter per enum, so new enums need no registration.
     */
    @ReadingConverter
    static class StringToWireValueConverterFactory implements ConverterFactory<String, WireValue> {

        @Override
        public <T extends WireValue> Converter<String, T> getConverter(Class<T> targetType) {
            return new StringToWireValue<>(targetType);
        }

        private record StringToWireValue<T extends WireValue>(Class<T> targetType)
                implements Converter<String, T> {

            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public T convert(String source) {
                return (T) WireValues.parse((Class) targetType, source);
            }
        }
    }
}
