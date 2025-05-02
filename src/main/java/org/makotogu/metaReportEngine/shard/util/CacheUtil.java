package org.makotogu.metaReportEngine.shard.util;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class CacheUtil {

    private final Cache<String, ReportConfigurationDto> reportConfCache;

    public void putReportConfig(String key, ReportConfigurationDto reportConfigurationDto) {
        log.debug("put report config to cache, key: {}, value: {}", key, reportConfigurationDto);
        reportConfCache.put(key, reportConfigurationDto);
    }
    public ReportConfigurationDto getReportConfig(String key) {
        return reportConfCache.getIfPresent(key);
    }

}
