package org.makotogu.metaReportEngine.datasource.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.makotogu.metaReportEngine.datasource.routing.BusinessSqlSessionTemplateRouter;
import org.makotogu.metaReportEngine.datasource.spi.DatasourceExecutor;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.makotogu.metaReportEngine.shard.exception.DatasourceConfigurationException;
import org.makotogu.metaReportEngine.shard.exception.DatasourceExecutionException;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MybatisDatasourceExecutor implements DatasourceExecutor {

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final BusinessSqlSessionTemplateRouter sqlSessionTemplateRouter;

    @Override
    public Object execute(ReportConfigurationDto.DataSourceConfig datasourceConfig, Map<String, Object> executionContext) {
        JsonNode paramMappingNode = datasourceConfig.getParamMapping();
        log.debug("MybatisDatasourceExecutor execute paramMappingNode: {}", paramMappingNode);
        String statementId = datasourceConfig.getQueryRef();
        String resultStructure = datasourceConfig.getResultStructure();
        String datasourceContext = datasourceConfig.getDatasourceContext(); // 获取数据源上下文标识

        // 1. 获取当前查询所需的 SqlSessionTemplate
        SqlSessionTemplate currentSqlSessionTemplate = sqlSessionTemplateRouter.getSqlSessionTemplate(datasourceContext);
        // 2. 使用SpringEL处理查询参数
        Map<String, Object> queryParams = prepareQueryParams(paramMappingNode, executionContext);
        // 3. 使用获取到的 Template 执行 MyBatis 查询
        Object result = null;
        try {
            if ("list_map".equalsIgnoreCase(resultStructure) || "list".equalsIgnoreCase(resultStructure)) {
                result = currentSqlSessionTemplate.selectList(statementId, queryParams);
            } else if ("single_map".equalsIgnoreCase(resultStructure) || "map".equalsIgnoreCase(resultStructure)) {
                result = currentSqlSessionTemplate.selectOne(statementId, queryParams);
            } else if ("scalar".equalsIgnoreCase(resultStructure)) {
                result = currentSqlSessionTemplate.selectOne(statementId, queryParams);
            } else {
                log.error("Unsupported result structure: {} for statement: {}", resultStructure, statementId);
                throw new DatasourceExecutionException("Unsupported result structure: " + resultStructure);
            }
        } catch (PersistenceException e) {
            log.error("MyBatis execution failed for statement: {} using context [{}], params: {}",
                    statementId, datasourceContext, queryParams, e);
            throw new DatasourceExecutionException("Failed to execute query: " + statementId, e);
        } catch (DatasourceConfigurationException e) { // Router 可能抛出配置异常
            log.error("Datasource configuration error for statement: {} using context [{}], params: {}",
                    statementId, datasourceContext, queryParams, e);
            throw e;
        }

        log.debug("Executed statement [{}] successfully using context [{}], params [{}], result type [{}]",
                statementId, datasourceContext, queryParams, result != null ? result.getClass().getSimpleName() : "null");
        return result;
    }

    private Map<String, Object> prepareQueryParams(JsonNode paramMappingNode, Map<String, Object> executionContext) {
        Map<String, Object> queryParams = new HashMap<>();
        if (paramMappingNode == null || paramMappingNode.isNull() || !paramMappingNode.isObject()) {
            return queryParams; // 没有参数需要映射
        }

        // 为每次执行创建新的上下文，避免如果上下文被修改可能导致的并发问题
        EvaluationContext evaluationContext = new StandardEvaluationContext();
        // 将整个 executionContext Map 作为一个名为 'context' 的变量设置进去
        // 这样表达式可以写成 '#context.customerId' 或 '#context.someList[0]'
        evaluationContext.setVariable("context", executionContext);

        Iterator<Map.Entry<String, JsonNode>> fields = paramMappingNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String paramName = entry.getKey();
            JsonNode expressionNode = entry.getValue();

            if (expressionNode == null || !expressionNode.isTextual()) {
                log.warn("Invalid expression for parameter '{}', expected a string expression, got: {}", paramName, expressionNode);
                continue;  // 跳过无效的表达式
            }

            String expressionString = expressionNode.asText();

            try {
                // 解析表达式字符串
                Expression exp = expressionParser.parseExpression(expressionString);

                // 使用上下文计算表达式的值
                Object paramValue = exp.getValue(evaluationContext);

                queryParams.put(paramName, paramValue);
                log.trace("Param mapping: '{}' evaluated from expression '{}' to value: {}", paramName, expressionString, paramValue);

            } catch (Exception e) { // 捕获 SpringEL 计算过程中可能抛出的各种异常
                log.error("Failed to evaluate SpEL expression '{}' for parameter '{}'", expressionString, paramName, e);
                // 决定如何处理错误：跳过该参数、使用 null，还是抛出异常？
                // 抛出异常通常更安全，以便提示配置问题
                throw new DatasourceExecutionException("Failed to evaluate parameter expression: " + expressionString, e);
            }
        }
        return queryParams;
    }

}
