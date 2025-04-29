package org.makotogu.metaReportEngine.metadata.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class ReportDataSource {

    private Long id;
    private String reportId;
    private String dataSourceName;
    private String dataSourceType;
    private String connectionUrl;
    private String username;
    private String password;
    private String query;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
