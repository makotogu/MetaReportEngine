package org.makotogu.metaReportEngine.transformer.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.shard.exception.TransformationException;
import org.makotogu.metaReportEngine.shard.util.SpelEvaluator;
import org.makotogu.metaReportEngine.transformer.spi.Transformer;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conditional Text Transformer: 根据 SpEL 条件表达式选择并输出文本模板.
 * 支持在模板中进行简单的变量替换 {{varName}}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConditionalTextTransformer implements Transformer {

    private static final String TRANSFORMER_TYPE = "CONDITIONAL_TEXT";
    // --- 配置项常量 ---
    private static final String CONFIG_CONDITION_KEY = "condition";     // SpEL 条件表达式 (字符串)
    private static final String CONFIG_TRUE_TEMPLATE_KEY = "trueTemplate";  // 条件为真时的模板 (字符串)
    private static final String CONFIG_FALSE_TEMPLATE_KEY = "falseTemplate"; // 可选: 条件为假时的模板 (字符串)

    // 用于简单模板变量替换的正则表达式: 匹配 {{ 非贪婪匹配 }}
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    private final SpelEvaluator spelEvaluator; // 注入 SpEL 工具类

    @Override
    public String getTransformerType() {
        return TRANSFORMER_TYPE;
    }

    @Override
    public Object transform(List<Object> inputs, JsonNode config, Map<String, Object> executionContext) throws TransformationException {
        // 1. 验证配置
        if (config == null || !config.hasNonNull(CONFIG_CONDITION_KEY) || !config.get(CONFIG_CONDITION_KEY).isTextual()
                || !config.hasNonNull(CONFIG_TRUE_TEMPLATE_KEY) /* trueTemplate 必须有 */ ) {
            throw new TransformationException(TRANSFORMER_TYPE,
                    String.format("Configuration error: Missing or invalid '%s' (text) or '%s' (text) in config: %s",
                            CONFIG_CONDITION_KEY, CONFIG_TRUE_TEMPLATE_KEY, config));
        }
        String conditionExpression = config.get(CONFIG_CONDITION_KEY).asText();
        String trueTemplate = config.get(CONFIG_TRUE_TEMPLATE_KEY).asText();
        // falseTemplate 是可选的
        String falseTemplate = config.path(CONFIG_FALSE_TEMPLATE_KEY).asText(""); // 默认为空字符串

        // 2. 评估 SpEL 条件表达式
        boolean conditionResult;
        try {
            // 期望条件表达式返回 Boolean 类型
            Boolean result = spelEvaluator.evaluate(conditionExpression, inputs, executionContext, Boolean.class);
            conditionResult = Boolean.TRUE.equals(result); // 处理 null 的情况，当成 false
            log.debug("[{}] Condition expression '{}' evaluated to: {}", TRANSFORMER_TYPE, conditionExpression, conditionResult);
        } catch (SpelEvaluationException e) {
            // 如果 SpEL 评估失败，包装成 TransformationException
            throw new TransformationException(TRANSFORMER_TYPE, "Failed to evaluate condition expression: " + conditionExpression, e);
        } catch (Exception e) { // 捕获其他意外错误
            throw new TransformationException(TRANSFORMER_TYPE, "Unexpected error evaluating condition: " + conditionExpression, e);
        }


        // 3. 根据条件结果选择模板
        String selectedTemplate = conditionResult ? trueTemplate : falseTemplate;

        // 4. (可选但常用) 执行简单的模板变量替换
        // 将模板中的 {{varName}} 替换为 executionContext 中对应 key 的值
        if (StringUtils.hasText(selectedTemplate) && selectedTemplate.contains("{{")) {
            try {
                selectedTemplate = replaceTemplateVariables(selectedTemplate, executionContext);
            } catch (Exception e) {
                log.error("[{}] Error during template variable replacement for template snippet: '{}'", TRANSFORMER_TYPE, selectedTemplate.substring(0, Math.min(50, selectedTemplate.length())), e);
                // 替换失败，可以选择返回原始模板或抛异常
                // throw new TransformationException(TRANSFORMER_TYPE, "Error replacing variables in template", e);
            }
        }

        // 5. 返回最终文本
        return selectedTemplate;
    }

    /**
     * 简单的模板变量替换。将 {{varName}} 替换为 executionContext 中 key 为 "varName" 的值。
     * 注意：这里使用的是简单的字符串替换，不是完整的 SpEL 评估。
     *      如果需要更复杂的表达式，应考虑使用专门的模板引擎或在 SpEL 条件中完成。
     */
    private String replaceTemplateVariables(String template, Map<String, Object> context) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1).trim(); // 获取括号内的变量名并去除空格
            Object value = context.get(varName);      // 从上下文中查找值
            String replacement = (value == null) ? "" : value.toString(); // 如果找不到或为null，替换为空字符串
            // 需要注意 $ 符号在 appendReplacement 中的特殊含义，进行转义
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}