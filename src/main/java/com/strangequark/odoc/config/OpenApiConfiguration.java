package com.strangequark.odoc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springdoc.core.customizers.OpenApiCustomizer;

/** Source-owned metadata for the generated versioned API contract. */
@Configuration
class OpenApiConfiguration {
    @Bean
    OpenAPI odocOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Odoc API")
                .version("v1")
                .description("Versioned API contract for the Odoc documentation workspace."));
    }

    /**
     * Keeps the Phase 0 contract deliberately small and independently generated.
     *
     * <p>The {@code thin-slice} profile is test infrastructure only. It needs the public system
     * probe and one idempotent command to prove the shared transport conventions, not every
     * product endpoint which happens to be present in a developer's local build. The command
     * controller itself is profile-gated, so it is never present in the normal production
     * contract/runtime.
     */
    @Bean
    @Profile("thin-slice")
    @SuppressWarnings("rawtypes") // springdoc 3.0 exposes Components schemas as a raw map.
    OpenApiCustomizer thinSliceOpenApiCustomizer() {
        return openApi -> {
            Paths restrictedPaths = new Paths();
            List.of("/api/v1/system/info", "/api/v1/test/commands/echo").forEach(path -> {
                PathItem item = openApi.getPaths().get(path);
                if (item != null) {
                    restrictedPaths.addPathItem(path, item);
                }
            });
            openApi.setPaths(restrictedPaths);

            Components components = openApi.getComponents();
            if (components != null && components.getSchemas() != null) {
                Map<String, Schema> thinSliceSchemas = new LinkedHashMap<>();
                List.of("SystemInfoResponse", "ThinSliceCommandRequest", "ThinSliceCommandResponse")
                        .forEach(schemaName -> {
                            Schema schema = components.getSchemas().get(schemaName);
                            if (schema != null) {
                                thinSliceSchemas.put(schemaName, schema);
                            }
                        });
                components.setSchemas(thinSliceSchemas);
            }
        };
    }
}
