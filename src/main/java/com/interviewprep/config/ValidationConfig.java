package com.interviewprep.config;

import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes temporal constraints such as {@code @FutureOrPresent} use the application {@link Clock}. */
@Configuration(proxyBeanMethods = false)
public class ValidationConfig {

    @Bean
    ValidationConfigurationCustomizer clockProviderCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
