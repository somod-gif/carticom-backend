package com.carticom.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = loadDotenv();
        if (props.isEmpty()) {
            return;
        }
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        // Lowest precedence: real OS environment variables always win.
        environment.getPropertySources().addLast(
                new MapPropertySource(PROPERTY_SOURCE_NAME, props));
    }

    private Map<String, Object> loadDotenv() {
        Map<String, Object> props = new HashMap<>();
        Path dotenv = Paths.get(System.getProperty("user.dir"), ".env");
        if (!Files.exists(dotenv)) {
            dotenv = Paths.get(System.getProperty("user.dir"), "backend", ".env");
        }
        if (!Files.exists(dotenv)) {
            return props;
        }
        try {
            for (String line : Files.readAllLines(dotenv)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                props.put(key, value);
            }
        } catch (IOException e) {
            // .env unreadable — fall back to defaults / OS env
        }
        return props;
    }
}
