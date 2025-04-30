package org.makotogu.metaReportEngine.shard.util;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class CacheUtil {

    private final Cache<Long, ReportConfigurationDto> reportConfCache;

    public void putReportConfig(Long reportId, ReportConfigurationDto reportConfigurationDto) {
        reportConfCache.put(reportId, reportConfigurationDto);
    }
    public ReportConfigurationDto getReportConfig(Long reportId) {
        return reportConfCache.getIfPresent(reportId);
    }

}
