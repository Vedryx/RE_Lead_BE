package com.vedryxtech.voiceagent.config;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.dashboard.domain.DashboardRange;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import com.vedryxtech.voiceagent.storage.ObjectStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets query parameters use the same camelCase enum values as the JSON bodies
 * ({@code ?pipelineStatus=retryScheduled} rather than {@code RETRY_SCHEDULED}).
 */
@Configuration
@EnableConfigurationProperties({CallPolicyProperties.class, ObjectStorageProperties.class})
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ActionType.class, ActionType::fromValue);
        registry.addConverter(String.class, LeadStatus.class, LeadStatus::fromValue);
        registry.addConverter(String.class, LeadPipelineStatus.class, LeadPipelineStatus::fromValue);
        registry.addConverter(String.class, LeadFinalStatus.class, LeadFinalStatus::fromValue);
        registry.addConverter(String.class, LeadStage.class, LeadStage::fromValue);
        registry.addConverter(String.class, CallOutcome.class, CallOutcome::fromValue);
        registry.addConverter(String.class, CallDisposition.class, CallDisposition::fromValue);
        registry.addConverter(String.class, RecordingStatus.class, RecordingStatus::fromValue);
        registry.addConverter(String.class, UserRole.class, UserRole::fromValue);
        registry.addConverter(String.class, DashboardRange.class, DashboardRange::fromValue);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
