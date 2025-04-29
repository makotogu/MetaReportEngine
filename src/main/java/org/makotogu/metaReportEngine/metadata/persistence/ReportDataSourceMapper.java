package org.makotogu.metaReportEngine.metadata.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.makotogu.metaReportEngine.metadata.entity.ReportDataSource;

import java.util.List;

@Mapper
public interface ReportDataSourceMapper {

    @Select("select * from report_datasource where report_def_id = #{reportDefId}")
    List<ReportDataSource> getReportDataSourcesByReportDefId(Long reportDefId);
}
