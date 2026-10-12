package com.stock.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration web MVC. La CORS est centralisée dans CorsConfig (source
 * unique, alimentée par la variable d'environnement CORS_ALLOWED_ORIGINS) et
 * appliquée par SecurityConfig — ne pas redéclarer de mapping CORS ici.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
