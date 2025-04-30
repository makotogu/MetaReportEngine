package org.makotogu.metaReportEngine.metadata.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ReportTransformationRule {

    private Long id;
    private String reportDefId;
    private String ruleAlias;
    private String transformerType;
    private List<String> inputRefs;
    private JsonNode config;
    private String outputVariableName;
    private List<String> dependencyRefs;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
