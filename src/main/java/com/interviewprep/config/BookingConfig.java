package com.interviewprep.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Booking feature wiring: schedule properties, the expiry sweep, and the executor that sends notifications. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BookingProperties.class)
@EnableAsync
@EnableScheduling
public class BookingConfig {

    public static final String NOTIFICATION_EXECUTOR = "bookingNotificationExecutor";

    private static final Logger log = LoggerFactory.getLogger(BookingConfig.class);

    /**
     * Small and bounded so a slow notification channel cannot exhaust threads or memory. When the queue is full the
     * notification is dropped with a warning instead of running on (and slowing down) the confirming request thread.
     */
    @Bean(NOTIFICATION_EXECUTOR)
    ThreadPoolTaskExecutor bookingNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("booking-notify-");
        executor.setRejectedExecutionHandler(
                (task, pool) -> log.warn("Booking notification queue is full; notification dropped"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
