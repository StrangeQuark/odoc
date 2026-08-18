package com.strangequark.odoc;

import com.strangequark.odoc.thinslice.ThinSliceApplication;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OdocApplication {

    public static void main(String[] args) {
        SpringApplication.run(applicationSource(args), args);
    }

    /**
     * The Phase 0 slice deliberately boots a smaller application, rather than merely hiding
     * product endpoints from generated API documentation. That keeps Flyway/JPA/domain
     * migrations out of the proof and prevents the test-only command from becoming a convenient
     * path into the main runtime.
     */
    static Class<?> applicationSource(String[] args) {
        return activeProfiles(args).anyMatch("thin-slice"::equals)
                ? ThinSliceApplication.class
                : OdocApplication.class;
    }

    private static java.util.stream.Stream<String> activeProfiles(String[] args) {
        return java.util.stream.Stream.concat(
                        Arrays.stream(args)
                                .filter(argument -> argument.startsWith("--spring.profiles.active="))
                                .map(argument -> argument.substring("--spring.profiles.active=".length())),
                        java.util.stream.Stream.of(
                                System.getProperty("spring.profiles.active", ""),
                                System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "")))
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }
}
