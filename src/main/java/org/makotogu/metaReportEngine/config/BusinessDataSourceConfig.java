package org.makotogu.metaReportEngine.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.ibatis.session.SqlSessionFactory;
import org.makotogu.metaReportEngine.config.handler.JacksonTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class BusinessDataSourceConfig {

    @Bean(name = "defaultBusinessDataSource")
    public DataSource DataSource( @Value("${spring.datasource.default.url}") String url,
                                  @Value("${spring.datasource.default.username}") String username,
                                  @Value("${spring.datasource.default.password}") String password,
                                  @Value("${spring.datasource.default.driver-class-name}") String driverClassName) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();
    }

    @Bean(name = "defaultBusinessSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("defaultBusinessDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        // 配置 Mapper XML 路径、MyBatis 配置等...
        // sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/risk/*.xml"));
        return sessionFactoryBean.getObject();
    }

    @Bean(name = "defaultBusinessSqlSessionTemplate") // Bean 名称作为 Qualifier
    public SqlSessionTemplate dbSqlSessionTemplate(@Qualifier("defaultBusinessSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        // 注册 JsonNode 类型的 TypeHandler
        sqlSessionFactory
                .getConfiguration()
                .getTypeHandlerRegistry()
                .register(JsonNode.class, new JacksonTypeHandler<>(JsonNode.class));
        // 启用自动驼峰命名规则映射
        sqlSessionFactory
                .getConfiguration()
                .setMapUnderscoreToCamelCase(true);
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "riskDataSource")
    public DataSource riskDataSource(@Value("${spring.datasource.risk.url}") String url,
                                     @Value("${spring.datasource.risk.username}") String username,
                                     @Value("${spring.datasource.risk.password}") String password,
                                     @Value("${spring.datasource.risk.driver-class-name}") String driverClassName) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();
    }

    @Bean(name = "riskSqlSessionFactory")
    public SqlSessionFactory riskSqlSessionFactory(@Qualifier("riskDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        // 配置 Mapper XML 路径、MyBatis 配置等...
        // sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/risk/*.xml"));
        return sessionFactoryBean.getObject();
    }

    @Bean(name = "riskDbSqlSessionTemplate") // Bean 名称作为 Qualifier
    public SqlSessionTemplate riskDbSqlSessionTemplate(@Qualifier("riskSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        // 注册 JsonNode 类型的 TypeHandler
        sqlSessionFactory
                .getConfiguration()
                .getTypeHandlerRegistry()
                .register(JsonNode.class, new JacksonTypeHandler<>(JsonNode.class));
        // 启用自动驼峰命名规则映射
        sqlSessionFactory
                .getConfiguration()
                .setMapUnderscoreToCamelCase(true);
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Configuration
    @MapperScan(basePackages = {"org.makotogu.metaReportEngine.test.mapper"},
            sqlSessionTemplateRef = "riskDbSqlSessionTemplate")
    public static class ConfigRiskDbMapperScanConfig {}

    // --- 为 CRM 数据库重复类似配置 ---
//    @Bean(name = "crmDataSource")
//    @ConfigurationProperties(prefix = "spring.datasource.crm")
//    public DataSource crmDataSource() {
//        return DataSourceBuilder.create().build();
//    }
//
//    @Bean(name = "crmSqlSessionFactory")
//    public SqlSessionFactory crmSqlSessionFactory(@Qualifier("crmDataSource") DataSource dataSource) throws Exception {
//        // ... 配置 ...
//    }
//
//    @Bean(name = "crmDbSqlSessionTemplate") // Bean 名称作为 Qualifier
//    public SqlSessionTemplate crmDbSqlSessionTemplate(@Qualifier("crmSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
//        return new SqlSessionTemplate(sqlSessionFactory);
//    }
}