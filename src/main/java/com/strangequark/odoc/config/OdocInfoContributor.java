package com.strangequark.odoc.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Releases only application/version/runtime mode information through the internal info endpoint. */
@Component
class OdocInfoContributor implements InfoContributor {
    private final Environment environment;

    OdocInfoContributor(Environment environment) { this.environment = environment; }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("application", "odoc")
                .withDetail("version", environment.getProperty("info.odoc.version", "dev"))
                .withDetail("runtime", environment.getProperty("odoc.runtime.mode", "API"));
    }
}
