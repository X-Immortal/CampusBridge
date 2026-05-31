package com.campusbridge.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redirect HTTP (80) to HTTPS (443).
 * Enable with: server.http.redirect.enabled=true in application.yml
 */
@Configuration
public class HttpToHttpsConfig {

    @Bean
    @ConditionalOnProperty(name = "server.http.redirect.enabled", havingValue = "true")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpRedirectCustomizer() {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setScheme("http");
            connector.setPort(80);
            connector.setSecure(false);
            connector.setRedirectPort(443);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
