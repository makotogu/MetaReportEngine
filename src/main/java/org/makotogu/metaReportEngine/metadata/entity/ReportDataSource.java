package org.makotogu.metaReportEngine.metadata.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ReportDataSource {

    private Long id;
    private String reportDefId;
    private String datasourceAlias;
    private String queryType;
    private String queryRef;
    private JsonNode paramMapping;
    private String resultStructure;
    private String description;
    private Integer executionOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String datasourceContext;
}
