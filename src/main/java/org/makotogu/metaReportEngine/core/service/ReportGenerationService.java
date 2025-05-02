package org.makotogu.metaReportEngine.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.datasource.impl.MybatisDatasourceExecutor;
import org.makotogu.metaReportEngine.datasource.spi.DatasourceExecutor;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.makotogu.metaReportEngine.metadata.service.MetadataService;
import org.makotogu.metaReportEngine.rendering.service.PoiTlRenderingService;
import org.makotogu.metaReportEngine.shard.exception.DatasourceExecutionException;
import org.makotogu.metaReportEngine.shard.exception.RenderingException;
import org.makotogu.metaReportEngine.shard.exception.ReportConfNotFoundException;
import org.makotogu.metaReportEngine.shard.exception.ReportGenerationException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportGenerationService {

    private final MetadataService metadataService;
    private final PoiTlRenderingService renderingService;
    private final DatasourceExecutor datasourceExecutor;

    public byte[] generateReport(String reportId, Map<String, Object> initialContext) throws ReportConfNotFoundException, RenderingException, ReportGenerationException {
        log.info("Starting report generation for reportId: {}, context: {}", reportId, initialContext);

        // 1. 加载配置 (已有 MetadataService 实现)
        ReportConfigurationDto config = metadataService.getReportConfiguration(reportId);
        log.debug("Loaded report configuration for reportId: {}", reportId);

        // 2. 初始化执行上下文 (可以简单合并初始上下文)
        Map<String, Object> executionContext = new HashMap<>(initialContext);
        // 可选: 放入一些默认上下文信息, 如 reportId, definition 等
        executionContext.put("reportId", reportId);
        executionContext.put("definition", config.getDefinition());
        executionContext.put("customerId", "客户编号");
        executionContext.put("custId", "custId");
        // ...

        // 3. (占位/模拟) 执行数据源查询
        // 3. 执行数据源查询
        log.info("Executing data sources...");
        if (config.getDataSources() != null) {
            // 注意：这里简单按列表顺序执行，如果需要按 executionOrder 或依赖执行，需要先排序或构建执行计划
            for (ReportConfigurationDto.DataSourceConfig dsConfig : config.getDataSources()) {
                try {
                    log.debug("Executing datasource: {}", dsConfig.getDatasourceAlias());
                    Object result = datasourceExecutor.execute(dsConfig, executionContext);
                    // 将查询结果放入上下文，使用 alias 作为 key
                    executionContext.put(dsConfig.getDatasourceAlias(), result);
                    log.debug("Datasource {} executed successfully.", dsConfig.getDatasourceAlias());
                } catch (DatasourceExecutionException e) {
                    log.error("Failed to execute datasource: {}", dsConfig.getDatasourceAlias(), e);
                    // 根据业务需求决定是继续执行其他数据源还是直接失败抛出异常
                    throw new ReportGenerationException("Datasource execution failed for alias: " + dsConfig.getDatasourceAlias(), e);
                }
            }
        }
        log.info("Data sources executed.");

        // 4. (占位/模拟) 执行转换规则
        // TODO: 实现 Transformer 调用和依赖分析逻辑
        // 暂时可以跳过，或者基于 config.getTransformationRules() 和模拟的输入数据，模拟转换结果放入 executionContext
        // 例如:
        // if ("MINI_CUST_SUMMARY_V1".equals(reportId)) { // 仅为示例
        //     executionContext.put("total_amount_raw", new BigDecimal("10000.50"));
        //     executionContext.put("final_formatted_total", "10,000.50 元");
        // }
        log.info("[MOCK] Skipping Transformation Rule Execution for now.");


        // 5. (占位/模拟) 准备渲染数据
        // TODO: 实现基于 TemplateMapping 配置从 executionContext 构建 renderData 的逻辑
        // 暂时可以硬编码或基于模拟数据构建
        Map<String, Object> renderData = new HashMap<>();
        if ("MINI_CUST_SUMMARY_V1".equals(reportId)) { // 仅为示例
            renderData.put("customer_name", executionContext.getOrDefault("cust_name_ds", "模拟姓名"));
            renderData.put("formatted_loan_total", executionContext.getOrDefault("final_formatted_total", "模拟总额"));
        } else {
            // 对于其他报告ID，可以返回空Map或根据情况处理
            log.warn("No mock render data prepared for reportId: {}", reportId);
        }
        log.info("Prepared mock render data: {}", renderData);


        // 6. 调用渲染层 (已有 RenderingService 实现)
        try {
            log.debug("Calling rendering service for template: {}", config.getDefinition().getTemplatePath());
            byte[] reportBytes = renderingService.renderReport(config.getDefinition().getTemplatePath(), renderData);
            log.info("Report successfully rendered for reportId: {}", reportId);
            return reportBytes;
        } catch (RenderingException e) {
            log.error("Rendering failed for reportId: {}", reportId, e);
            throw e; // 直接向上抛出，或包装成 ReportGenerationException
        } catch (Exception e) {
            log.error("Unexpected error during report generation for reportId: {}", reportId, e);
            throw new ReportGenerationException("Unexpected error during report generation for " + reportId, e); // 假设有这个异常
        }
    }
}
