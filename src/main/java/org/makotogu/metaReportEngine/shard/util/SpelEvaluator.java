package org.makotogu.metaReportEngine.shard.util;

import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.shard.exception.SpelEvaluationException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SpEL 表达式评估工具类。
 * 提供安全的 SpEL 执行环境。
 */
@Component // 作为 Spring Bean，方便注入和管理
@Slf4j
public class SpelEvaluator {

    private final ExpressionParser expressionParser;

    public SpelEvaluator() {
        this.expressionParser = new SpelExpressionParser();
        log.info("SpelEvaluator initialized.");
        // 未来可以在此配置 Parser 的行为 (SpelParserConfiguration)
    }

    /**
     * 使用提供的 EvaluationContext 评估 SpEL 表达式。
     *
     * @param expressionString 要评估的 SpEL 表达式字符串。
     * @param context          预先构建好的评估上下文。
     * @param expectedType     期望返回的类型 Class 对象。
     * @param <T>              期望返回的类型。
     * @return 评估结果，类型为 T。
     * @throws SpelEvaluationException 如果评估过程中发生错误。
     */
    public <T> T evaluate(String expressionString, EvaluationContext context, Class<T> expectedType) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            return null;
        }
        if (context == null) {
            throw new IllegalArgumentException("EvaluationContext cannot be null.");
        }

        try {
            Expression expression = expressionParser.parseExpression(expressionString);
            T result = expression.getValue(context, expectedType);
            log.trace("SpEL expression '{}' evaluated to: {}", expressionString, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to evaluate SpEL expression: '{}' with provided context.", expressionString, e);
            throw new SpelEvaluationException("Failed to evaluate SpEL expression: " + expressionString, e);
        }
    }

    /**
     * 评估 SpEL 表达式。
     *
     * @param expressionString 要评估的 SpEL 表达式字符串。
     * @param inputs           Transformer 的直接输入列表，将在 SpEL 上下文中作为 #inputs 变量。
     * @param executionContext 完整的执行上下文 Map，将在 SpEL 上下文中作为 #context 变量。
     * @param expectedType     期望返回的类型 Class 对象 (例如 Boolean.class, String.class)。
     * @param <T>              期望返回的类型。
     * @return 评估结果，类型为 T。
     * @throws SpelEvaluationException 如果评估过程中发生错误。
     */
    public <T> T evaluate(String expressionString, List<Object> inputs, Map<String, Object> executionContext, Class<T> expectedType) {
        if (expressionString == null || expressionString.trim().isEmpty()) {
            log.warn("Received empty or null SpEL expression string.");
            // 根据需要返回 null 或抛异常
            return null;
            // throw new IllegalArgumentException("Expression string cannot be null or empty.");
        }

        // 1. 创建评估上下文
        // 每次评估都创建一个新的 StandardEvaluationContext 是最安全的，避免状态污染
        EvaluationContext context = new StandardEvaluationContext();
        // 设置变量，让表达式可以通过 #inputs 和 #context 访问数据
        context.setVariable("inputs", inputs); // 将整个输入列表作为变量 #inputs
        if (inputs != null && !inputs.isEmpty()) {
            // 可选: 将第一个输入作为 #input0 或 #root 方便访问？
            context.setVariable("input0", inputs.get(0)); // 访问第一个输入
        }
        context.setVariable("context", executionContext); // 将完整上下文作为变量 #context

        // TODO: 安全性配置 - 按需限制 SpEL 的能力
        // 例如，移除 BeanResolver、设置只读属性访问器等，防止恶意表达式
        // context.setBeanResolver(null);
        // context.setPropertyAccessors(...); // 只保留需要的访问器

        try {
            // 2. 解析表达式
            Expression expression = expressionParser.parseExpression(expressionString);

            // 3. 评估表达式并获取期望类型的结果
            T result = expression.getValue(context, expectedType);
            log.trace("SpEL expression '{}' evaluated to: {}", expressionString, result);
            return result;

        } catch (Exception e) { // 捕获所有 SpEL 相关的异常
            log.error("Failed to evaluate SpEL expression: '{}'", expressionString, e);
            // 包装成自定义异常或直接抛出运行时异常
            throw new SpelEvaluationException("Failed to evaluate SpEL expression: " + expressionString, e);
        }
    }

    // 可以添加其他重载方法，例如不指定期望类型（返回 Object），或者只传入 executionContext 等
    public Object evaluate(String expressionString, EvaluationContext context) {
        return evaluate(expressionString, context, Object.class);
    }
    public Object evaluate(String expressionString, List<Object> inputs, Map<String, Object> executionContext) {
        return evaluate(expressionString, inputs, executionContext, Object.class);
    }
}

// 需要定义 SpelEvaluationException
// package com.yourcompany.metareportengine.shared.exception;
// public class SpelEvaluationException extends RuntimeException { ... }