package org.makotogu.metaReportEngine.datasource.spi;

import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;

import java.util.Map;

public interface DatasourceExecutor {

    /**
     * 根据数据源配置执行查询并返回结果。
     *
     * @param datasourceConfig 数据源配置 DTO
     * @param executionContext 当前执行上下文，用于获取参数值
     * @return 查询结果 (通常是 List<Map<String, Object>>, Map<String, Object>, 或 Object)
     * @throws DatasourceExecutionException 如果查询执行失败
     */
    Object execute(ReportConfigurationDto.DataSourceConfig datasourceConfig, Map<String, Object> executionContext);

}
