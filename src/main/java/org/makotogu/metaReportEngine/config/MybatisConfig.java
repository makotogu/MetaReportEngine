package org.makotogu.metaReportEngine.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.ibatis.session.SqlSessionFactory;
import org.makotogu.metaReportEngine.config.handler.JacksonTypeHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;


@MapperScan(value = {"org.makotogu.metaReportEngine.metadata.*"}, sqlSessionFactoryRef = "defaultBusinessSqlSessionFactory")
@Configuration
public class MybatisConfig {

    private final SqlSessionFactory sqlSessionFactory;

    public MybatisConfig(@Qualifier("defaultBusinessSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @PostConstruct
    public void addTypeHandlers() {
        // 注册 JsonNode 类型的 TypeHandler
        sqlSessionFactory
                .getConfiguration()
                .getTypeHandlerRegistry()
                .register(JsonNode.class, new JacksonTypeHandler<>(JsonNode.class));
        // 启用自动驼峰命名规则映射
        sqlSessionFactory
                .getConfiguration()
                .setMapUnderscoreToCamelCase(true);
    }
}
