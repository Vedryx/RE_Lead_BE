package com.vedryxtech.voiceagent.settings.application;

import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import com.vedryxtech.voiceagent.settings.persistence.AppSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class SettingsServiceImpl implements SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsServiceImpl.class);

    private final AppSettingsRepository repository;

    public SettingsServiceImpl(AppSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    public AppSettings current() {
        return repository.findById(AppSettings.SINGLETON_ID)
                .orElseGet(this::createDefault);
    }

    @Override
    public CallPolicy currentPolicy() {
        CallPolicy policy = current().getCallPolicy();
        return policy != null ? policy : CallPolicy.defaults();
    }

    @Override
    public AppSettings updateCallPolicy(CallPolicy policy) {
        AppSettings settings = current();
        settings.setCallPolicy(policy != null ? policy : CallPolicy.defaults());
        settings.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return repository.save(settings);
    }

    @Override
    public AppSettings save(AppSettings settings) {
        settings.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return repository.save(settings);
    }

    private AppSettings createDefault() {
        AppSettings settings = new AppSettings();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        AppSettings saved = repository.save(settings);
        log.info("Created default app_settings singleton");
        return saved;
    }
}
