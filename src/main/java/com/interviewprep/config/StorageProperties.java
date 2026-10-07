package com.interviewprep.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * File upload storage settings ({@code app.storage.*}).
 *
 * @param location directory the uploaded bytes are written to (env {@code APP_STORAGE_LOCATION}); created at startup if
 *     missing. A relative path resolves against the working directory.
 * @param maxFileSize largest accepted file. {@code spring.servlet.multipart.max-file-size} is set from this value so
 *     oversized uploads are rejected while parsing; the service re-checks it as defense in depth.
 */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
        @DefaultValue("uploads") @NotBlank String location,
        @DefaultValue("5MB") @NotNull DataSize maxFileSize) {}
