package org.makotogu.metaReportEngine.metadata.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ReportTemplateMapping {

    private Long id;
    private String reportDefId;
    private String templateTag;
    private String dataSourceRef;
    private String dataExpression;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
