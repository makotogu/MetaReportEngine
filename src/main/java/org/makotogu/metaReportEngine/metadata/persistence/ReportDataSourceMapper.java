package org.makotogu.metaReportEngine.metadata.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.JdbcType;
import org.makotogu.metaReportEngine.metadata.entity.ReportDataSource;

import java.util.List;

@Mapper
public interface ReportDataSourceMapper {

    @Select("select * from report_datasource where report_def_id = #{reportDefId}")
    @Results({
        @Result(property = "paramMapping", column = "param_mapping", jdbcType = JdbcType.OTHER, typeHandler = org.makotogu.metaReportEngine.config.handler.JacksonTypeHandler.class)
    })
    List<ReportDataSource> getReportDataSourcesByReportDefId(Long reportDefId);
}
