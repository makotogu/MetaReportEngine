package org.makotogu.metaReportEngine.transformer.spi.impl;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.shard.exception.TransformationException;
import org.makotogu.metaReportEngine.transformer.spi.Transformer;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Formatter Transformer: 根据配置中的 pattern 格式化输入值 (数字或日期/时间).
 */
@Service
@Slf4j
public class FormatterTransformer implements Transformer {

    private static final String TRANSFORMER_TYPE = "FORMATTER";
    private static final String CONFIG_PATTERN_KEY = "pattern"; // 配置中格式化模式的 key

    @Override
    public String getTransformerType() {
        return TRANSFORMER_TYPE;
    }

    @Override
    public Object transform(List<Object> inputs, JsonNode config, Map<String, Object> executionContext) throws TransformationException {
        // 1. 验证输入列表
        if (CollectionUtils.isEmpty(inputs)) {
            log.warn("FormatterTransformer received empty or null inputs. Returning null.");
            return null; // 或者返回空字符串 "", 或抛异常，根据业务需求决定
        }

        // 2. 获取要格式化的值 (通常取第一个输入)
        Object valueToFormat = inputs.get(0);
        if (valueToFormat == null) {
            log.warn("Input value at index 0 for formatting is null. Returning null.");
            return null; // 或者返回空字符串 ""
        }

        // 3. 验证并获取配置中的 pattern
        if (config == null || !config.hasNonNull(CONFIG_PATTERN_KEY) || !config.get(CONFIG_PATTERN_KEY).isTextual()) {
            throw new TransformationException(TRANSFORMER_TYPE,
                    String.format("Configuration error: Missing or invalid '%s' (text) in config: %s", CONFIG_PATTERN_KEY, config));
        }
        String pattern = config.get(CONFIG_PATTERN_KEY).asText();
        log.debug("FormatterTransformer processing value type: {}, using pattern: {}", valueToFormat.getClass().getName(), pattern);

        // 4. 根据值的类型进行格式化
        try {
            if (valueToFormat instanceof Number) {
                // 处理数字类型 (包括 BigDecimal, Integer, Long, Double etc.)
                return formatNumber((Number) valueToFormat, pattern);
            } else if (valueToFormat instanceof Date) {
                // 处理旧版 java.util.Date
                return formatDate((Date) valueToFormat, pattern);
            } else if (valueToFormat instanceof TemporalAccessor) {
                // 处理 Java 8 Time API 类型 (LocalDate, LocalDateTime, ZonedDateTime, Instant etc.)
                return formatTemporal((TemporalAccessor) valueToFormat, pattern);
            } else {
                // 其他不支持的类型
                log.warn("Unsupported type for formatting [{}]: {}. Returning its string representation.",
                        TRANSFORMER_TYPE, valueToFormat.getClass().getName());
                // 可以选择抛出异常，或者返回原始值的字符串形式
                return valueToFormat.toString(); // 返回原始字符串形式，可能不是期望的
                // 或者更严格：
                // throw new TransformationException(TRANSFORMER_TYPE, "Unsupported data type for formatting: " + valueToFormat.getClass().getName());
            }
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // 捕获特定于格式化的异常 (无效模式等)
            log.error("Invalid pattern '{}' for value type {} in rule [{}].",
                    pattern, valueToFormat.getClass().getName(), TRANSFORMER_TYPE, e);
            throw new TransformationException(TRANSFORMER_TYPE, "Invalid format pattern '" + pattern + "' for value type " + valueToFormat.getClass().getName(), e);
        } catch (Exception e) {
            // 捕获其他意外错误
            log.error("Unexpected error during formatting in rule [{}]. Value: {}, Pattern: {}",
                    TRANSFORMER_TYPE, valueToFormat, pattern, e);
            throw new TransformationException(TRANSFORMER_TYPE, "Unexpected error during formatting: " + e.getMessage(), e);
        }
    }

    /**
     * 格式化数字。
     */
    private String formatNumber(Number number, String pattern) {
        try {
            // 对于 BigDecimal，使用 toPlainString 避免科学计数法问题
            NumberFormat formatter = new DecimalFormat(pattern);
            if (number instanceof BigDecimal) {
                return formatter.format(((BigDecimal) number)); // DecimalFormat 可以直接处理 BigDecimal
            } else {
                return formatter.format(number); // 处理 Integer, Long, Double 等
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid DecimalFormat pattern: " + pattern, e);
        }
    }

    /**
     * 格式化 java.util.Date。
     */
    private String formatDate(Date date, String pattern) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern);
            return formatter.format(date);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid SimpleDateFormat pattern: " + pattern, e);
        }
    }

    /**
     * 格式化 Java 8 Time API 类型 (TemporalAccessor)。
     */
    private String formatTemporal(TemporalAccessor temporal, String pattern) {
        try {
            // DateTimeFormatter 是线程安全的，可以考虑缓存，但每次创建通常也很快
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            // 对于需要时区但输入不含时区的情况 (如 LocalDateTime)，可能需要指定默认时区
            // ZoneId defaultZone = ZoneId.systemDefault();
            // formatter = formatter.withZone(defaultZone); // 按需添加
            return formatter.format(temporal);
        } catch (IllegalArgumentException | DateTimeParseException e) { // DateTimeParseException 用于解析无效日期时间字符串，这里主要是 IllegalArgumentException
            throw new IllegalArgumentException("Invalid DateTimeFormatter pattern: " + pattern, e);
        } catch (java.time.temporal.UnsupportedTemporalTypeException e) {
            log.error("Pattern '{}' requires temporal information not available in value of type {}",
                    pattern, temporal.getClass().getName(), e);
            throw new TransformationException(TRANSFORMER_TYPE, "Pattern '" + pattern
                    + "' cannot be applied to the provided date/time type: " + temporal.getClass().getName(), e);
        }
    }
}