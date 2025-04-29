package org.makotogu.metaReportEngine.metadata.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class ReportParameter {

    private Long id;
    private String reportId;
    private String parameterName;
    private String parameterType;
    private String defaultValue;
    private Boolean isRequired;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
