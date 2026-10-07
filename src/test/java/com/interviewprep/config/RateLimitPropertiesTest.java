package com.interviewprep.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** The quota is configuration, not code: defaults apply, overrides bind, and unusable values stop startup. */
class RateLimitPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class PropertiesConfig {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    void defaultsToTenRequestsPerMinute() {
        runner.run(context -> {
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);
            assertThat(properties.limit()).isEqualTo(10);
            assertThat(properties.window()).isEqualTo(Duration.ofMinutes(1));
        });
    }

    @Test
    void limitAndWindowCanBeOverridden() {
        runner.withPropertyValues("app.rate-limit.limit=100", "app.rate-limit.window=30s")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.limit()).isEqualTo(100);
                    assertThat(properties.window()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void nonPositiveLimitFailsStartup() {
        runner.withPropertyValues("app.rate-limit.limit=0")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("app.rate-limit.limit"));
    }

    @Test
    void subSecondWindowFailsStartup() {
        runner.withPropertyValues("app.rate-limit.window=500ms")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("app.rate-limit.window"));
    }
}
