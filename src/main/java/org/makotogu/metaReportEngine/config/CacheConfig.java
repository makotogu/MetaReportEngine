package org.makotogu.metaReportEngine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, ReportConfigurationDto> reportConfCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}
