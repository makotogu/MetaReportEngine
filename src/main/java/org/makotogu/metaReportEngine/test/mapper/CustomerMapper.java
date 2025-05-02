package org.makotogu.metaReportEngine.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerMapper {

    @Select("select concat('test', #{custId});")
    String getCustomerName(@Param("custId")String customerId);
}
