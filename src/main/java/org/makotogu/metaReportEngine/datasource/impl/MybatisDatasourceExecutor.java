package org.makotogu.metaReportEngine.datasource.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.datasource.spi.DatasourceExecutor;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class MybatisDatasourceExecutor implements DatasourceExecutor {

    @Override
    public Object execute(ReportConfigurationDto.DataSourceConfig datasourceConfig, Map<String, Object> executionContext) {
        JsonNode paramMapping = datasourceConfig.getParamMapping();
        log.debug("MybatisDatasourceExecutor execute paramMapping: {}", paramMapping);

        return null;
    }
}
