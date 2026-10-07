package com.interviewprep;

import org.springframework.boot.SpringApplication;

/** Runs the application locally against a Testcontainers PostgreSQL (requires Docker). */
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.from(Application::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
