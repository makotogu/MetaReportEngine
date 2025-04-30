package org.makotogu.metaReportEngine.metadata.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;
import org.makotogu.metaReportEngine.metadata.entity.ReportTransformationRule;

import java.util.List;

@Mapper
public interface ReportTransformationRuleMapper {

    @Select("select * from report_transformation_rule where report_def_id = #{reportDefId}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "reportDefId", column = "report_def_id"),
        @Result(property = "ruleAlias", column = "rule_alias"),
        @Result(property = "transformerType", column = "transformer_type"),
        @Result(property = "inputRefs", column = "input_refs", jdbcType = JdbcType.OTHER, typeHandler = org.makotogu.metaReportEngine.config.handler.JacksonTypeHandler.class),
        @Result(property = "config", column = "config", jdbcType = JdbcType.OTHER, typeHandler = org.makotogu.metaReportEngine.config.handler.JacksonTypeHandler.class),
        @Result(property = "outputVariableName", column = "output_variable_name"),
        @Result(property = "dependencyRefs", column = "dependency_refs", jdbcType = JdbcType.OTHER, typeHandler = org.makotogu.metaReportEngine.config.handler.JacksonTypeHandler.class),
        @Result(property = "description", column = "description"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    List<ReportTransformationRule> getTransformationRulesByReportDefId(Long reportDefId);
}
