package org.makotogu.metaReportEngine.datasource.routing;

import org.makotogu.metaReportEngine.shard.exception.DatasourceConfigurationException;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class BusinessSqlSessionTemplateRouter {

    // 注入所有业务 SqlSessionTemplate Bean，使用 Map (Key 是 Bean 的名称/Qualifier)
    @Autowired
    private Map<String, SqlSessionTemplate> businessSqlSessionTemplates;

    // (可选) 定义一个默认的 SqlSessionTemplate，如果 context 为 null 或找不到时使用
    @Autowired(required = false) // required = false 避免没有默认配置时启动失败
    @Qualifier("defaultBusinessSqlSessionTemplate") // 假设你配置了一个名为 defaultBusinessSqlSessionTemplate 的 Bean
    private SqlSessionTemplate defaultSqlSessionTemplate;

    @PostConstruct
    public void init() {
        log.info("Initialized BusinessSqlSessionTemplateRouter with templates for contexts: {}", businessSqlSessionTemplates.keySet());
        if (defaultSqlSessionTemplate == null) {
            log.warn("No default Business SqlSessionTemplate configured.");
        }
    }

    /**
     * 根据上下文标识符获取对应的 SqlSessionTemplate。
     *
     * @param contextIdentifier 上下文标识符 (e.g., "risk", "crm")，对应 SqlSessionTemplate Bean 的 Qualifier/Name
     * @return 对应的 SqlSessionTemplate
     * @throws DatasourceConfigurationException 如果找不到对应的 Template 且没有默认值
     */
    public SqlSessionTemplate getSqlSessionTemplate(String contextIdentifier) {
        SqlSessionTemplate selectedTemplate = null;

        if (contextIdentifier != null && businessSqlSessionTemplates.containsKey(contextIdentifier + "DbSqlSessionTemplate")) {
            // 尝试根据 contextIdentifier + "DbSqlSessionTemplate" 获取 (匹配我们之前的 Bean 名称)
            selectedTemplate = businessSqlSessionTemplates.get(contextIdentifier + "DbSqlSessionTemplate");
            log.debug("Routing to SqlSessionTemplate with key: {}", contextIdentifier + "DbSqlSessionTemplate");
        } else if (contextIdentifier != null && businessSqlSessionTemplates.containsKey(contextIdentifier)) {
            // 或者直接尝试使用 contextIdentifier 作为 Key (如果 Bean 名称就是它)
            selectedTemplate = businessSqlSessionTemplates.get(contextIdentifier);
            log.debug("Routing to SqlSessionTemplate with key: {}", contextIdentifier);
        }


        if (selectedTemplate != null) {
            return selectedTemplate;
        } else {
            // 如果找不到指定的，尝试返回默认的
            if (defaultSqlSessionTemplate != null) {
                log.warn("SqlSessionTemplate not found for context '{}', falling back to default.", contextIdentifier);
                return defaultSqlSessionTemplate;
            } else {
                log.error("SqlSessionTemplate not found for context '{}' and no default is configured.", contextIdentifier);
                throw new DatasourceConfigurationException("No suitable SqlSessionTemplate found for context: " + contextIdentifier); // 需要定义这个异常
            }
        }
    }
}