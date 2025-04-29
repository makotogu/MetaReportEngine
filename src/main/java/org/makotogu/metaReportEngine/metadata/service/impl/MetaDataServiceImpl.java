package org.makotogu.metaReportEngine.metadata.service.impl;

import lombok.AllArgsConstructor;
import org.makotogu.metaReportEngine.metadata.entity.ReportDefinition;
import org.makotogu.metaReportEngine.metadata.persistence.ReportDefinitionMapper;
import org.makotogu.metaReportEngine.metadata.service.MetadataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MetaDataServiceImpl implements MetadataService {

    private final ReportDefinitionMapper reportDefinitionMapper;

    @Override
    public List<ReportDefinition> getAllReportDefinition() {
        return reportDefinitionMapper.getAllReportDefinition();
    }
}
