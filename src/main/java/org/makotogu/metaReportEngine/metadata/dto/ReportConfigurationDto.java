package org.makotogu.metaReportEngine.metadata.dto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.makotogu.metaReportEngine.metadata.entity.ReportDataSource;
import org.makotogu.metaReportEngine.metadata.entity.ReportDefinition;
import org.makotogu.metaReportEngine.metadata.entity.ReportTemplateMapping;
import org.makotogu.metaReportEngine.metadata.entity.ReportTransformationRule;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class ReportConfigurationDto {

    private ReportDefinitionInfo definition;
    private List<DataSourceConfig> dataSources;
    private List<RuleConfig> transformationRules;
    private List<MappingConfig> templateMappings;

    public void setDefinition(ReportDefinition reportDefinition) {
        this.definition = new ReportDefinitionInfo(reportDefinition);
    }
    public void setDataSources(List<ReportDataSource> reportDataSources) {
        this.dataSources = reportDataSources.stream()
                .map(DataSourceConfig::new)
                .collect(Collectors.toList());
    }
    public void setTransformationRules(List<ReportTransformationRule> reportTransformationRules) {
        this.transformationRules = reportTransformationRules.stream()
                .map(RuleConfig::new)
                .collect(Collectors.toList());
    }
    public void setTemplateMappings(List<ReportTemplateMapping> reportTemplateMappings) {
        this.templateMappings = reportTemplateMappings.stream()
                .map(MappingConfig::new)
                .collect(Collectors.toList());
    }

    @Data
    public static class ReportDefinitionInfo {
        private String reportId;
        private String reportName;
        private String templatePath;
        private String version;
        private String description;
        // 可以根据需要添加 status 等其他字段

        public ReportDefinitionInfo (ReportDefinition reportDefinition) {
            this.reportId = reportDefinition.getReportId();
            this.reportName = reportDefinition.getReportName();
            this.templatePath = reportDefinition.getTemplatePath();
            this.version = reportDefinition.getVersion();
            this.description = reportDefinition.getDescription();
        }
    }

    @Data
    public static class DataSourceConfig {
        private String datasourceAlias;
        private String queryType;
        private String queryRef;
        private JsonNode paramMapping; // 使用 JsonNode 保持灵活性
        private String resultStructure;
        private int executionOrder;

        public DataSourceConfig(ReportDataSource reportDataSource) {
            this.datasourceAlias = reportDataSource.getDatasourceAlias();
            this.queryType = reportDataSource.getQueryType();
            this.queryRef = reportDataSource.getQueryRef();
            this.paramMapping = reportDataSource.getParamMapping();
            this.resultStructure = reportDataSource.getResultStructure();
            this.executionOrder = reportDataSource.getExecutionOrder();
        }
    }

    @Data
    public static class RuleConfig {
        private String ruleAlias;
        private String transformerType;
        private List<String> inputRefs;    // 解析后的输入引用列表
        private JsonNode config;           // Transformer 的具体配置
        private String outputVariableName;
        private List<String> dependencyRefs; // 解析后的显式依赖列表

        public RuleConfig(ReportTransformationRule reportTransformationRule) {
            this.ruleAlias = reportTransformationRule.getRuleAlias();
            this.transformerType = reportTransformationRule.getTransformerType();
            this.inputRefs = reportTransformationRule.getInputRefs();
            this.config = reportTransformationRule.getConfig();
            this.outputVariableName = reportTransformationRule.getOutputVariableName();
            this.dependencyRefs = reportTransformationRule.getDependencyRefs();
        }
    }

    @Data
    public static class MappingConfig {
        private String templateTag;
        private String dataSourceRef;
        private String dataExpression;

        public MappingConfig(ReportTemplateMapping reportTemplateMapping) {
            this.templateTag = reportTemplateMapping.getTemplateTag();
            this.dataSourceRef = reportTemplateMapping.getDataSourceRef();
            this.dataExpression = reportTemplateMapping.getDataExpression();
        }
    }
}