package org.makotogu.metaReportEngine.metadata.persistence;

import org.apache.ibatis.annotations.Select;
import org.makotogu.metaReportEngine.metadata.entity.ReportTransformationRule;

import java.util.List;

public interface ReportTransformationRuleMapper {

    @Select("select * from report_transformation_rule where report_def_id = #{reportDefId}")
    List<ReportTransformationRule> getTransformationRulesByReportDefId(Long reportDefId);
}
