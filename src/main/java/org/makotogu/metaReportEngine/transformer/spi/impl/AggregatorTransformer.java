package org.makotogu.metaReportEngine.transformer.spi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.shard.exception.TransformationException;
import org.makotogu.metaReportEngine.transformer.spi.Transformer;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode; // 用于 AVG 计算
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong; // 用于 COUNT
import java.util.stream.Collectors;

/**
 * Aggregator Transformer: 对输入的列表数据执行聚合计算 (SUM, AVG, COUNT).
 */
@Service
@Slf4j
public class AggregatorTransformer implements Transformer {

    private static final String TRANSFORMER_TYPE = "AGGREGATOR";
    private static final String CONFIG_FIELD_KEY = "field";     // 配置中指定要聚合的字段名
    private static final String CONFIG_FUNCTION_KEY = "function"; // 配置中指定聚合函数 (SUM, AVG, COUNT)
    private static final String CONFIG_SCALE_KEY = "scale";       // 可选配置，用于 AVG 的小数位数
    private static final String CONFIG_ROUNDING_MODE_KEY = "roundingMode"; // 可选配置，用于 AVG 的舍入模式

    @Override
    public String getTransformerType() {
        return TRANSFORMER_TYPE;
    }

    @Override
    public Object transform(List<Object> inputs, JsonNode config, Map<String, Object> executionContext) throws TransformationException {
        // 1. 验证输入列表 (聚合器通常需要列表作为输入)
        if (CollectionUtils.isEmpty(inputs) || !(inputs.get(0) instanceof List)) {
            log.warn("AggregatorTransformer expects a non-empty List as the first input, but received: {}. Returning null.",
                    inputs != null ? inputs.getClass().getName() : "null");
            // 对于 COUNT 函数，空列表应该返回 0，对于 SUM/AVG 返回 null 或 0 可能是合理的
            // 我们将在后面根据函数类型处理空列表
            // return null;
        }
        // 断言或确保输入是 List<Map> 或类似结构，这里简化处理，假设输入就是 List
        List<?> inputList = (List<?>) inputs.get(0);


        // 2. 验证并获取配置
        if (config == null || !config.hasNonNull(CONFIG_FIELD_KEY) || !config.get(CONFIG_FIELD_KEY).isTextual()
                || !config.hasNonNull(CONFIG_FUNCTION_KEY) || !config.get(CONFIG_FUNCTION_KEY).isTextual()) {
            throw new TransformationException(TRANSFORMER_TYPE,
                    String.format("Configuration error: Missing or invalid '%s' (text) or '%s' (text) in config: %s",
                            CONFIG_FIELD_KEY, CONFIG_FUNCTION_KEY, config));
        }
        String field = config.get(CONFIG_FIELD_KEY).asText();
        String function = config.get(CONFIG_FUNCTION_KEY).asText().toUpperCase(); // 转大写方便比较

        // 处理空列表输入的情况
        if (CollectionUtils.isEmpty(inputList)) {
            log.debug("AggregatorTransformer received an empty list for aggregation.");
            if ("COUNT".equals(function)) {
                return 0L; // COUNT of empty list is 0
            } else {
                // For SUM/AVG of empty list, returning 0 or null depends on requirements. Let's return 0 BigDecimal for consistency.
                return BigDecimal.ZERO; // 返回 BigDecimal 类型的 0
                // return null;
            }
        }

        // 3. 执行聚合计算
        try {
            switch (function) {
                case "SUM":
                    return calculateSum(inputList, field);
                case "AVG":
                    // AVG 可能需要额外的 scale 和 roundingMode 配置
                    int scale = config.path(CONFIG_SCALE_KEY).asInt(2); // 默认2位小数
                    RoundingMode roundingMode = parseRoundingMode(config.path(CONFIG_ROUNDING_MODE_KEY).asText("HALF_UP")); // 默认四舍五入
                    return calculateAvg(inputList, field, scale, roundingMode);
                case "COUNT":
                    // Count 通常只是列表的大小，但如果需要 count 特定字段非 null 的值，可以调整逻辑
                    return calculateCount(inputList, field); // 也可以简单返回 inputList.size()
                default:
                    throw new TransformationException(TRANSFORMER_TYPE, "Unsupported aggregation function: " + function);
            }
        } catch (ClassCastException | NullPointerException e) {
            // 处理数据类型不匹配或空指针 (例如，尝试对非数字字段求和)
            log.error("Data type error during aggregation for field '{}' in rule [{}]. Check input data.", field, TRANSFORMER_TYPE, e);
            throw new TransformationException(TRANSFORMER_TYPE, "Data type error during aggregation for field '" + field + "'", e);
        } catch (Exception e) {
            log.error("Unexpected error during aggregation for field '{}' in rule [{}]: {}", field, TRANSFORMER_TYPE, e.getMessage(), e);
            throw new TransformationException(TRANSFORMER_TYPE, "Unexpected error during aggregation for field '" + field + "': " + e.getMessage(), e);
        }
    }

    /**
     * 计算列表中指定字段的总和。
     * 假设列表元素是 Map，字段值是数字或可转换为 BigDecimal 的字符串。
     */
    private BigDecimal calculateSum(List<?> list, String field) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Object item : list) {
            Object value = getValueFromItem(item, field);
            if (value instanceof Number) {
                sum = sum.add(new BigDecimal(value.toString()));
            } else if (value instanceof String && StringUtils.hasText((String)value)) {
                try {
                    sum = sum.add(new BigDecimal((String) value));
                } catch (NumberFormatException e) {
                    log.warn("Cannot parse string '{}' to BigDecimal for SUM aggregation on field '{}'. Skipping.", value, field);
                }
            }
            // null 或其他类型的值会被忽略
        }
        return sum;
    }

    /**
     * 计算列表中指定字段的平均值。
     */
    private BigDecimal calculateAvg(List<?> list, String field, int scale, RoundingMode roundingMode) {
        BigDecimal sum = BigDecimal.ZERO;
        long count = 0;
        for (Object item : list) {
            Object value = getValueFromItem(item, field);
            boolean added = false;
            if (value instanceof Number) {
                sum = sum.add(new BigDecimal(value.toString()));
                added = true;
            } else if (value instanceof String && StringUtils.hasText((String)value)) {
                try {
                    sum = sum.add(new BigDecimal((String) value));
                    added = true;
                } catch (NumberFormatException e) {
                    log.warn("Cannot parse string '{}' to BigDecimal for AVG aggregation on field '{}'. Skipping.", value, field);
                }
            }
            if (added) {
                count++;
            }
        }
        if (count == 0) {
            log.warn("Cannot calculate average for field '{}' as no valid numeric values were found.", field);
            return BigDecimal.ZERO; // 或抛异常，或返回 null
        }
        return sum.divide(BigDecimal.valueOf(count), scale, roundingMode);
    }

    /**
     * 计算列表中指定字段非空值的数量 (或者简单地返回列表大小)。
     */
    private Long calculateCount(List<?> list, String field) {
        // 方案一：简单返回列表大小
        // return (long) list.size();

        // 方案二：计算指定字段非 null 值的数量
        AtomicLong count = new AtomicLong(0);
        for (Object item : list) {
            Object value = getValueFromItem(item, field);
            if (value != null) {
                // 可以增加更严格的检查，比如非空字符串等
                count.incrementAndGet();
            }
        }
        return count.get();
    }

    /**
     * 从列表项 (假设是 Map) 中安全地获取指定字段的值。
     */
    @SuppressWarnings("unchecked") // 忽略类型转换警告
    private Object getValueFromItem(Object item, String field) {
        if (item instanceof Map) {
            return ((Map<String, Object>) item).get(field);
        }
        // TODO: 可以添加对 POJO 的反射支持 (如果输入可能是 List<POJO>)
        log.trace("Item is not a Map, cannot extract field '{}'. Item type: {}", field, item != null ? item.getClass().getName() : "null");
        return null;
    }

    /**
     * 解析舍入模式字符串。
     */
    private RoundingMode parseRoundingMode(String modeStr) {
        try {
            return RoundingMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid rounding mode string: '{}'. Falling back to HALF_UP.", modeStr);
            return RoundingMode.HALF_UP; // 默认四舍五入
        }
    }
}