package com.campusbridge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /** 所有「现在几点」的判断都走这个时钟，测试里可以换成固定时间。 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
