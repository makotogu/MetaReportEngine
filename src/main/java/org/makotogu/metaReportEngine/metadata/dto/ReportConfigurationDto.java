package org.makotogu.metaReportEngine.metadata.dto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class ReportConfigurationDto {

    private ReportDefinitionInfo definition;
    private List<DataSourceConfig> dataSources;
    private List<RuleConfig> transformationRules;
    private List<MappingConfig> templateMappings;


    @Data
    public static class ReportDefinitionInfo {
        private String reportId;
        private String reportName;
        private String templatePath;
        private String version;
        private String description;
        // 可以根据需要添加 status 等其他字段
    }

    @Data
    public static class DataSourceConfig {
        private String datasourceAlias;
        private String queryType;
        private String queryRef;
        private JsonNode paramMapping; // 使用 JsonNode 保持灵活性
        private String resultStructure;
        private int executionOrder;
    }

    @Data
    public static class RuleConfig {
        private String ruleAlias;
        private String transformerType;
        private List<String> inputRefs;    // 解析后的输入引用列表
        private JsonNode config;           // Transformer 的具体配置
        private String outputVariableName;
        private List<String> dependencyRefs; // 解析后的显式依赖列表
    }

    @Data
    public static class MappingConfig {
        private String templateTag;
        private String dataSourceRef;
        private String dataExpression;
    }
}