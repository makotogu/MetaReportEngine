package org.makotogu.metaReportEngine.metadata.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class ReportDefinition {

    private Long id;
    private String reportId;
    private String reportName;
    private String templatePath;
    private String description;
    private String version;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
