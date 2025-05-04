package org.makotogu.metaReportEngine.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.datasource.impl.MybatisDatasourceExecutor;
import org.makotogu.metaReportEngine.datasource.spi.DatasourceExecutor;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.makotogu.metaReportEngine.metadata.service.MetadataService;
import org.makotogu.metaReportEngine.rendering.service.PoiTlRenderingService;
import org.makotogu.metaReportEngine.shard.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportGenerationService {

    private final MetadataService metadataService;
    private final TransformerExecutor transformerExecutor;
    private final DatasourceExecutor datasourceExecutor;
    private final PoiTlRenderingService renderingService;

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
        executionContext.put("reportGenDate", LocalDate.now()); // 或者 new Date() 等
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

        // 4. 执行转换规则 (替换模拟逻辑)
        log.info("Executing transformation rules for reportId: {}", reportId);
        if (!CollectionUtils.isEmpty(config.getTransformationRules())) {
            // TODO: 实现正确的依赖分析和执行顺序控制 (DAG), 目前简化为按列表顺序
            List<ReportConfigurationDto.RuleConfig> rulesToExecute = config.getTransformationRules();
            // List<ReportConfigurationDto.RuleConfig> rulesToExecute = determineExecutionOrder(config.getTransformationRules()); // 未来替换为这行

            for (ReportConfigurationDto.RuleConfig ruleConfig : rulesToExecute) {
                try {
                    // 4.1 解析输入数据
                    log.debug("Resolving inputs for rule: {}", ruleConfig.getRuleAlias());
                    List<Object> inputs = resolveInputs(ruleConfig.getInputRefs(), executionContext, ruleConfig.getRuleAlias());
                    log.debug("Inputs resolved for rule {}: {}", ruleConfig.getRuleAlias(), inputs); // 注意：日志中打印对象可能暴露敏感信息

                    // 4.2 调用 TransformerExecutor 执行转换
                    log.debug("Executing transformer for rule: {}", ruleConfig.getRuleAlias());
                    Object result = transformerExecutor.executeTransformer(
                            ruleConfig.getTransformerType(),
                            inputs,
                            ruleConfig.getConfig(), // 传入 JsonNode 配置
                            executionContext,       // 传入完整上下文
                            ruleConfig.getRuleAlias() // 传入规则别名用于错误报告
                    );

                    // 4.3 将结果放入上下文
                    executionContext.put(ruleConfig.getOutputVariableName(), result);
                    log.debug("Rule '{}' executed successfully, output variable '{}' set.", ruleConfig.getRuleAlias(), ruleConfig.getOutputVariableName());

                } catch (TransformationException | ReportGenerationException e) { // 捕获转换异常和输入解析异常
                    log.error("Failed to execute transformation rule: {}", ruleConfig.getRuleAlias(), e);
                    // 决定是继续执行其他规则还是立即失败
                    throw new ReportGenerationException("Transformation rule execution failed for alias: " + ruleConfig.getRuleAlias(), e);
                } catch (Exception e) { // 捕获其他未预料异常
                    log.error("Unexpected error during transformation rule execution: {}", ruleConfig.getRuleAlias(), e);
                    throw new ReportGenerationException("Unexpected error during transformation for alias: " + ruleConfig.getRuleAlias(), e);
                }
            }
        }
        log.info("Transformation rules executed for reportId: {}", reportId);


        // 5. 准备渲染数据
        log.info("Preparing render data for reportId: {}", reportId);
        Map<String, Object> renderData = new HashMap<>();
        List<String> tableRenderKeys = new ArrayList<>();
        if (!CollectionUtils.isEmpty(config.getTemplateMappings())) {
            // TODO: 实现 SpEL 支持 dataExpression (如果需要)
            for (ReportConfigurationDto.MappingConfig mappingConfig : config.getTemplateMappings()) {
                String dataSourceRef = mappingConfig.getDataSourceRef();
                String templateTag = mappingConfig.getTemplateTag();
                // 从执行上下文中获取最终的数据
                Object dataValue = executionContext.get(dataSourceRef);

                // 简单的去除标签符号获取key (可能需要更健壮的逻辑)
                String renderKey = templateTag.replaceAll("[{}]", "").replace("#", "").replace("@", "");

                if (dataValue != null) {
                    // TODO: 如果 mappingConfig.getDataExpression() 不为空, 在这里使用 SpEL 对 dataValue 求值
                    renderData.put(renderKey, dataValue);
                    if ("TABLE_BUILDER".equals(getProducingTransformerType(dataSourceRef, config))) { // 获取来源类型
                        tableRenderKeys.add(renderKey);
                    }
                    log.trace("Mapping template tag '{}' to render key '{}' with value from ref '{}'", templateTag, renderKey, dataSourceRef);
                } else {
                    log.warn("Data source ref '{}' for template tag '{}' not found in execution context. Tag will likely be empty.", dataSourceRef, templateTag);
                    // 可以选择放入 null 或空字符串，或不放入
                    // renderData.put(renderKey, null);
                }
            }
        }
        log.info("Render data prepared for reportId: {}", reportId);
        log.debug("Final render data map: {}", renderData); // 注意：可能包含敏感信息


        // 6. 调用渲染层 (已有 RenderingService 实现)
        try {
            log.debug("Calling rendering service for template: {}", config.getDefinition().getTemplatePath());
            byte[] reportBytes = renderingService.renderReport(config.getDefinition().getTemplatePath(), renderData, tableRenderKeys);
            log.info("Report successfully rendered for reportId: {}", reportId);
            return reportBytes;
        } catch (RenderingException e) {
            log.error("Rendering failed for reportId: {}", reportId, e);
            throw new ReportGenerationException("Rendering failed for " + reportId, e); // 包装成生成异常
        } catch (Exception e) {
            log.error("Unexpected error during report generation for reportId: {}", reportId, e);
            throw new ReportGenerationException("Unexpected error during report generation for " + reportId, e); // 假设有这个异常
        }
    }

    private String getProducingTransformerType(String dataSourceRef, ReportConfigurationDto config) {
        List<ReportConfigurationDto.RuleConfig> tableRules = config.getTransformationRules().stream().filter(transformationRule -> transformationRule.getOutputVariableName().equals(dataSourceRef)).collect(Collectors.toList());
        if (tableRules.size() > 1) {
            throw new ReportGenerationException("Multiple transformation rules produce the same output variable: " + dataSourceRef);
        }
        return tableRules.isEmpty() ? null : tableRules.get(0).getTransformerType();
    }

    /**
     * 根据输入引用列表从执行上下文中解析输入数据。
     *
     * @param inputRefs        输入引用列表 (包含 datasource_alias 或 rule_alias)
     * @param executionContext 当前执行上下文
     * @param currentRuleAlias 当前执行的规则别名 (用于错误报告)
     * @return 解析后的输入对象列表
     * @throws ReportGenerationException 如果某个输入引用在上下文中找不到对应的值
     */
    private List<Object> resolveInputs(List<String> inputRefs, Map<String, Object> executionContext, String currentRuleAlias) throws ReportGenerationException {
        if (CollectionUtils.isEmpty(inputRefs)) {
            return Collections.emptyList();
        }

        List<Object> inputs = new ArrayList<>(inputRefs.size());
        for (String inputRef : inputRefs) {
            if (executionContext.containsKey(inputRef)) {
                inputs.add(executionContext.get(inputRef));
            } else {
                // 输入数据缺失，这是一个严重的配置或流程错误，应该抛出异常
                log.error("Input reference '{}' not found in execution context for rule '{}'. Available keys: {}",
                        inputRef, currentRuleAlias, executionContext.keySet());
                throw new ReportGenerationException(String.format(
                        "Configuration error for rule '%s': Required input reference '%s' not found in execution context.",
                        currentRuleAlias, inputRef
                ));
            }
        }
        return inputs;
    }

    // TODO (未来): 实现 determineExecutionOrder 方法，用于处理规则的依赖关系 (DAG 拓扑排序)
    // private List<ReportConfigurationDto.RuleConfig> determineExecutionOrder(List<ReportConfigurationDto.RuleConfig> rules) { ... }


}
