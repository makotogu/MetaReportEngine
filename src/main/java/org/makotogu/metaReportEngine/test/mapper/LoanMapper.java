package org.makotogu.metaReportEngine.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LoanMapper {

    @Select("select 1 as id, 'c_1' as customer_id, length(#{customerId}) as amount union all " +
            "select 2 as id, 'c_2' as customer_id, length(#{customerId}) * 2 as amount ;")
    List<Map<String, Object>> getActiveLoans(@Param("customerId")String customerId);
}
