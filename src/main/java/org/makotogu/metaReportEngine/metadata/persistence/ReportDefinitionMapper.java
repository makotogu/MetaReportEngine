package org.makotogu.metaReportEngine.metadata.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.makotogu.metaReportEngine.metadata.entity.ReportDefinition;


import java.util.List;

@Mapper
public interface ReportDefinitionMapper {

    @Select("select * from report_definition;")
    List<ReportDefinition> getAllReportDefinition();

    @Select("select * from report_definition where report_id = #{reportId}")
    ReportDefinition getReportDefinitionById(@Param("reportId") String reportId);
}
