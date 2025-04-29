package org.makotogu.metaReportEngine.metadata.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class ReportTemplate {

    private Long id;
    private String reportId;
    private String templateName;
    private String templateType;
    private String templateContent;
    private String outputFormat;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
