package org.makotogu.metaReportEngine.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LoanMapper {

    @Select("select 1 as \"loanId\", 'a' as type, now() as \"issueDate\", 'c_1' as customer_id, length(#{customerId})  as amount union all " +
            "select 2 as \"loanId\", 'b' as type, now() as \"issueDate\",  'c_2' as customer_id, 2.5 * 10000 * 10000 as amount ;")
    List<Map<String, Object>> getActiveLoans(@Param("customerId")String customerId);
}
