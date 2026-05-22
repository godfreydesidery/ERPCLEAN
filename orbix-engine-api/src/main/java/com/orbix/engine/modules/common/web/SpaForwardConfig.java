package com.orbix.engine.modules.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Set;

/**
 * Serves the Angular bundle from {@code classpath:/static/} and forwards deep
 * links (e.g. {@code /admin/users}, {@code /catalog/items}) to {@code index.html}
 * so the Angular router can take over. API / actuator / swagger paths are left
 * alone — controllers handle them and unknown {@code /api/...} URLs fall through
 * to {@link com.orbix.engine.modules.common.service.GlobalExceptionHandler} as
 * 404 JSON, not the HTML shell.
 *
 * <p>Used by the single-container QA build that bakes {@code dist/} into the jar.
 * No effect in dev (where {@code /static/} is empty and the bundle is served by
 * {@code ng serve}).
 */
@Configuration
public class SpaForwardConfig implements WebMvcConfigurer {

    private static final Set<String> BACKEND_PREFIXES = Set.of(
        "api/", "actuator/", "v3/", "swagger-ui/", "swagger-ui.html"
    );

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    // Don't shadow backend routes — let them fall through to controllers
                    // (or to the GlobalExceptionHandler's 404 JSON for unknown API paths).
                    for (String prefix : BACKEND_PREFIXES) {
                        if (resourcePath.startsWith(prefix)) {
                            return null;
                        }
                    }
                    Resource resource = location.createRelative(resourcePath);
                    if (resource.exists() && resource.isReadable()) {
                        return resource;
                    }
                    // SPA fallback: deep link like /admin/users → serve index.html so
                    // the Angular router resolves the route client-side.
                    Resource index = new ClassPathResource("/static/index.html");
                    return index.exists() ? index : null;
                }
            });
    }
}
