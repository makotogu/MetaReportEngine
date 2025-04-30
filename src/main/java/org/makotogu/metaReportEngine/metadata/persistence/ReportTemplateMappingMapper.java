package org.makotogu.metaReportEngine.metadata.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.makotogu.metaReportEngine.metadata.entity.ReportTemplateMapping;

import java.util.List;

@Mapper
public interface ReportTemplateMappingMapper {

    @Select("select * from report_template_mapping where report_def_id = #{reportDefId}")
    List<ReportTemplateMapping> getReportTemplateMappingsByReportDefId(@Param("reportDefId") Long reportDefId);
}
