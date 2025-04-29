package org.makotogu.metaReportEngine.metadata.persistence;

import org.apache.ibatis.annotations.Select;
import org.makotogu.metaReportEngine.metadata.entity.ReportDefinition;
import org.mybatis.spring.annotation.MapperScan;

import java.util.List;

@MapperScan
public interface ReportDefinitionMapper {

    @Select("select * from report_definition;")
    List<ReportDefinition> getAllReportDefinition();
}
