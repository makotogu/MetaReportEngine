package org.makotogu.metaReportEngine.transformer.spi.impl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.shard.exception.TransformationException;
import org.makotogu.metaReportEngine.transformer.spi.Transformer;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unit Converter Transformer: 根据配置的阈值和单位自动转换输入数字的单位并格式化输出.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UnitConverterTransformer implements Transformer {

    private static final String TRANSFORMER_TYPE = "UNIT_CONVERTER";
    // --- 配置项常量 ---
    private static final String CONFIG_THRESHOLDS_KEY = "thresholds"; // 阈值列表 (数字数组)
    private static final String CONFIG_UNITS_KEY = "units";         // 单位列表 (字符串数组, 比阈值多一个)
    private static final String CONFIG_PRECISION_KEY = "precision";    // 小数精度 (整数)
    private static final String CONFIG_TEMPLATE_KEY = "template";     // 输出格式模板 (字符串, e.g., "{{value}} {{unit}}")
    private static final String CONFIG_ROUNDING_MODE_KEY = "roundingMode"; // 可选: 舍入模式 (字符串, 默认 HALF_UP)
    private static final String CONFIG_BASE_UNIT_KEY = "baseUnit";   // 可选: 基础单位 (字符串, 默认第一个单位)
    private static final String CONFIG_USE_GROUPING_KEY = "useGrouping"; // 可选: 是否使用千分位 (布尔, 默认 true)

    // ObjectMapper 最好是注入或共享的，这里仅为示例
    private final ObjectMapper objectMapper;

    @Override
    public String getTransformerType() {
        return TRANSFORMER_TYPE;
    }

    @Override
    public Object transform(List<Object> inputs, JsonNode config, Map<String, Object> executionContext) throws TransformationException {
        // 1. 验证输入 (通常是单个数字)
        if (CollectionUtils.isEmpty(inputs)) {
            log.warn("[{}] received empty or null inputs. Returning null.", TRANSFORMER_TYPE);
            return null;
        }
        Object inputValue = inputs.get(0);
        if (inputValue == null) {
            log.warn("[{}] input value at index 0 is null. Returning null.", TRANSFORMER_TYPE);
            return null;
        }

        // 2. 将输入值转换为 BigDecimal
        BigDecimal originalValue;
        try {
            if (inputValue instanceof BigDecimal) {
                originalValue = (BigDecimal) inputValue;
            } else if (inputValue instanceof Number) {
                originalValue = new BigDecimal(inputValue.toString());
            } else if (inputValue instanceof String) {
                // 尝试解析字符串, 允许带逗号的数字
                String stringValue = ((String) inputValue).replace(",", "");
                if (stringValue.isEmpty()) {
                    log.warn("[{}] input string value is empty. Returning null.", TRANSFORMER_TYPE);
                    return null;
                }
                originalValue = new BigDecimal(stringValue);
            } else {
                log.warn("[{}] received unsupported input type: {}. Returning null.", TRANSFORMER_TYPE, inputValue.getClass().getName());
                return null; // 或者抛出异常
                // throw new TransformationException(TRANSFORMER_TYPE, "Unsupported input type: " + inputValue.getClass().getName());
            }
        } catch (NumberFormatException e) {
            log.warn("[{}] failed to parse input value '{}' to BigDecimal. Returning null.", TRANSFORMER_TYPE, inputValue, e);
            return null; // 或者抛出异常
            // throw new TransformationException(TRANSFORMER_TYPE, "Cannot parse input value to number: " + inputValue, e);
        }

        // 3. 解析配置
        UnitConversionConfig conversionConfig = parseConfig(config);

        // 4. 执行单位转换和格式化
        try {
            return formatWithUnit(originalValue, conversionConfig);
        } catch (Exception e) {
            // 捕获内部处理可能发生的异常
            log.error("[{}] Error during unit conversion or formatting for value {}: {}", TRANSFORMER_TYPE, originalValue, e.getMessage(), e);
            throw new TransformationException(TRANSFORMER_TYPE, "Error during unit conversion/formatting: " + e.getMessage(), e);
        }
    }

    /**
     * 解析配置 JSON 到内部配置对象。
     */
    private UnitConversionConfig parseConfig(JsonNode config) throws TransformationException {
        if (config == null || config.isNull()) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration is missing.");
        }

        // 验证并解析 thresholds
        if (!config.hasNonNull(CONFIG_THRESHOLDS_KEY) || !config.get(CONFIG_THRESHOLDS_KEY).isArray()) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: Missing or invalid '" + CONFIG_THRESHOLDS_KEY + "' (numeric array).");
        }
        List<BigDecimal> thresholds = new ArrayList<>();
        try {
            for (JsonNode node : config.get(CONFIG_THRESHOLDS_KEY)) {
                if (!node.isNumber()) throw new IllegalArgumentException("Threshold must be a number.");
                thresholds.add(node.decimalValue());
            }
            // 阈值应该按升序排列
            Collections.sort(thresholds);
        } catch (Exception e) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: Invalid value in '" + CONFIG_THRESHOLDS_KEY + "'. " + e.getMessage() , e);
        }


        // 验证并解析 units
        if (!config.hasNonNull(CONFIG_UNITS_KEY) || !config.get(CONFIG_UNITS_KEY).isArray()) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: Missing or invalid '" + CONFIG_UNITS_KEY + "' (string array).");
        }
        List<String> units;
        try {
            units = objectMapper.readValue(config.get(CONFIG_UNITS_KEY).traverse(), new TypeReference<List<String>>() {});
        } catch (IOException e) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: Cannot parse '" + CONFIG_UNITS_KEY + "' as string array.", e);
        }

        // 单位数量必须比阈值多一个
        if (units.size() != thresholds.size() + 1) {
            throw new TransformationException(TRANSFORMER_TYPE,
                    String.format("Configuration error: Number of units (%d) must be exactly one more than the number of thresholds (%d).", units.size(), thresholds.size()));
        }

        // 解析 precision
        if (!config.hasNonNull(CONFIG_PRECISION_KEY) || !config.get(CONFIG_PRECISION_KEY).isInt()) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: Missing or invalid '" + CONFIG_PRECISION_KEY + "' (integer).");
        }
        int precision = config.get(CONFIG_PRECISION_KEY).asInt();

        // 解析 template
        if (!config.hasNonNull(CONFIG_TEMPLATE_KEY) || !config.get(CONFIG_TEMPLATE_KEY).isTextual()) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: Missing or invalid '" + CONFIG_TEMPLATE_KEY + "' (string).");
        }
        String template = config.get(CONFIG_TEMPLATE_KEY).asText();

        // 解析可选配置
        RoundingMode roundingMode = parseRoundingMode(config.path(CONFIG_ROUNDING_MODE_KEY).asText("HALF_UP"));
        String baseUnit = config.path(CONFIG_BASE_UNIT_KEY).asText(units.get(0)); // 默认使用第一个单位作为基础单位
        boolean useGrouping = config.path(CONFIG_USE_GROUPING_KEY).asBoolean(true); // 默认使用千分位

        return new UnitConversionConfig(thresholds, units, precision, template, roundingMode, baseUnit, useGrouping);
    }

    /**
     * 根据配置执行单位转换和格式化。
     */
    private String formatWithUnit(BigDecimal value, UnitConversionConfig config) {
        BigDecimal displayValue = value;
        String unit = config.getBaseUnit(); // 默认为基础单位
        BigDecimal divisor = BigDecimal.ONE; // 默认除数

        // 遍历阈值，找到合适的单位和除数
        for (int i = 0; i < config.getThresholds().size(); i++) {
            // 使用绝对值比较阈值，使得负数也能正确转换单位
            if (value.abs().compareTo(config.getThresholds().get(i)) >= 0) {
                // 如果当前值大于等于阈值，则使用下一个单位和对应的除数
                // 注意：除数通常是基于阈值计算的，但这里简化为直接使用阈值作为除数
                // 一个更精确的设计是，配置中直接提供除数列表，或者基于10000, 100000000等标准单位
                // 这里我们假设阈值就是转换的基数 (例如 10000 代表万, 100000000 代表亿)
                divisor = config.getThresholds().get(i); // 使用阈值作为除数
                unit = config.getUnits().get(i + 1);     // 使用下一个单位
                // 如果有多个阈值，需要确保找到最大的那个适用的阈值
                // 当前逻辑是找到第一个满足条件的就用，如果阈值升序，则会用最高的适用单位
            } else {
                // 如果值小于当前阈值，则停止查找，使用当前的 divisor 和 unit
                break;
            }
        }
        // 如果 divisor 大于 1 才进行除法计算
        if (divisor.compareTo(BigDecimal.ONE) > 0) {
            displayValue = value.divide(divisor, config.getPrecision() + 5, config.getRoundingMode()); // 保留更多精度进行中间计算
        }

        // 格式化最终显示的值
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(config.getPrecision());
        df.setMinimumFractionDigits(config.getPrecision()); // 保证显示指定位数的小数
        df.setRoundingMode(config.getRoundingMode());
        df.setGroupingUsed(config.isUseGrouping()); // 是否使用千分位
        String formattedValue = df.format(displayValue.setScale(config.getPrecision(), config.getRoundingMode())); // 最终舍入

        // 应用输出模板
        return config.getTemplate()
                .replace("{{value}}", formattedValue)
                .replace("{{unit}}", unit);
    }

    /**
     * 解析舍入模式字符串。
     */
    private RoundingMode parseRoundingMode(String modeStr) {
        // (同 AggregatorTransformer 中的实现)
        try {
            return RoundingMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid rounding mode string: '{}'. Falling back to HALF_UP.", modeStr);
            return RoundingMode.HALF_UP;
        }
    }

    // 内部类用于存储解析后的配置
    private static class UnitConversionConfig {
        private final List<BigDecimal> thresholds;
        private final List<String> units;
        private final int precision;
        private final String template;
        private final RoundingMode roundingMode;
        private final String baseUnit;
        private final boolean useGrouping;

        public UnitConversionConfig(List<BigDecimal> thresholds, List<String> units, int precision, String template,
                                    RoundingMode roundingMode, String baseUnit, boolean useGrouping) {
            this.thresholds = thresholds;
            this.units = units;
            this.precision = precision;
            this.template = template;
            this.roundingMode = roundingMode;
            this.baseUnit = baseUnit;
            this.useGrouping = useGrouping;
        }
        // 省略 Getters...
        public List<BigDecimal> getThresholds(){ return thresholds; }
        public List<String> getUnits() { return units; }
        public int getPrecision() { return precision; }
        public String getTemplate() { return template; }
        public RoundingMode getRoundingMode() { return roundingMode; }
        public String getBaseUnit() { return baseUnit; }
        public boolean isUseGrouping() { return useGrouping; }

    }
}