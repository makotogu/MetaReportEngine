package org.makotogu.metaReportEngine.metadata.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class ReportTransformationRule {

    private Long id;
    private String reportDefId;
    private String ruleAlias;
    private String transformerType;
    private JsonNode inputRefs;
    private JsonNode config;
    private String outputVariableName;
    private JsonNode dependencyRefs;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
