package org.makotogu.metaReportEngine.api.controller;

import lombok.AllArgsConstructor;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.makotogu.metaReportEngine.metadata.entity.ReportDefinition;
import org.makotogu.metaReportEngine.metadata.service.MetadataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/reportDefinition")
public class ReportDefinitionController {

    private final MetadataService metadataService;

    @GetMapping("/getReportDefinition/{reportId}")
    public ReportConfigurationDto getAllReportDefinition(@PathVariable String reportId) {
        return metadataService.getReportConfiguration(reportId);
    }

}
