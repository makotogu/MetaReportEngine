package org.makotogu.metaReportEngine.transformer.spi.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.makotogu.metaReportEngine.shard.exception.SpelEvaluationException;
import org.makotogu.metaReportEngine.shard.exception.TransformationException;
import org.makotogu.metaReportEngine.shard.util.SpelEvaluator;
import org.makotogu.metaReportEngine.transformer.spi.Transformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext; // 引入 EvaluationContext
import org.springframework.expression.spel.support.StandardEvaluationContext; // 引入 StandardEvaluationContext
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException; // For ObjectMapper exception
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TableBuilderTransformer implements Transformer {

    // --- 常量定义 (保持不变) ---
    private static final String TRANSFORMER_TYPE = "TABLE_BUILDER";
    private static final String CONFIG_COLUMNS_KEY = "columns";
    private static final String CONFIG_TOTAL_ROW_KEY = "totalRow";
    private static final String COL_HEADER_KEY = "header";
    private static final String COL_VALUE_EXPR_KEY = "valueExpression";
    private static final String COL_OUTPUT_KEY = "outputKey";
    private static final String COL_FORMATTER_KEY = "formatter";
    private static final String TOTAL_ENABLED_KEY = "enabled";
    private static final String TOTAL_LABEL_COL_KEY = "labelColumn";
    private static final String TOTAL_LABEL_VALUE_KEY = "labelValue";
    private static final String TOTAL_SUM_COLS_KEY = "sumColumns";
    private static final String TOTAL_AVG_COLS_KEY = "avgColumns";
    private static final String TOTAL_FORMATTERS_KEY = "formatters";
    private static final String TOTAL_SCALE_KEY = "avgScale";
    private static final String TOTAL_ROUNDING_MODE_KEY = "avgRoundingMode";


    private final SpelEvaluator spelEvaluator;
    private final ObjectMapper objectMapper;

    @Override
    public String getTransformerType() {
        return TRANSFORMER_TYPE;
    }

    @Override
    public Object transform(List<Object> inputs, JsonNode config, Map<String, Object> executionContext) throws TransformationException {
        // 1. 验证输入列表
        if (CollectionUtils.isEmpty(inputs) || !(inputs.get(0) instanceof List)) {
            log.warn("[{}] expects a non-empty List as the first input. Returning empty list.", TRANSFORMER_TYPE);
            return Collections.emptyList();
        }
        List<?> inputList = (List<?>) inputs.get(0);
        if (inputList.isEmpty()) {
            log.debug("[{}] received an empty list. Returning empty list.", TRANSFORMER_TYPE);
            return Collections.emptyList();
        }

        // 2. 解析配置
        TableBuildConfig tableConfig = parseConfig(config);

        // 3. 准备基础 SpEL 上下文 (只包含全局 #context) - 不再需要这一步，在循环内创建

        // 4. 遍历输入数据，构建输出表格行
        List<Map<String, Object>> outputTable = new ArrayList<>(inputList.size());
        // 用于存储每行需要聚合的原始数值 Map<列Key, 数值>
        List<Map<String, BigDecimal>> numericValuesForTotal = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < inputList.size(); rowIndex++) {
            Object rowInputObject = inputList.get(rowIndex);
            if (!(rowInputObject instanceof Map)) {
                log.warn("[{}] Skipping row {} because it is not a Map. Type: {}", TRANSFORMER_TYPE, rowIndex, rowInputObject != null ? rowInputObject.getClass().getName() : "null");
                continue;
            }
            Map<String, Object> rowInputMap = (Map<String, Object>) rowInputObject;
            Map<String, Object> outputRow = new HashMap<>();
            Map<String, BigDecimal> currentRowNumerics = tableConfig.needsTotalOrAvg() ? new HashMap<>() : null;

            // --- 为当前行创建一个特定的 SpEL 上下文 ---
            EvaluationContext rowSpelContext = new StandardEvaluationContext();
            rowSpelContext.setVariable("context", executionContext); // 放入全局上下文 #context
            rowSpelContext.setVariable("row", rowInputMap);       // 当前行数据设为 #row
            rowSpelContext.setVariable("rowIndex", rowIndex);     // 可选: 行索引设为 #rowIndex
            // ---------------------------------------

            // 遍历列定义，计算并填充输出行
            for (ColumnConfig column : tableConfig.getColumns()) {
                Object cellRawValue = null; // 存储 SpEL 计算的原始值
                Object cellDisplayValue = null; // 存储最终（可能格式化后）的显示值

                try {
                    // 使用行特定的 SpEL 上下文评估表达式
                    cellRawValue = spelEvaluator.evaluate(column.getValueExpression(), rowSpelContext, Object.class); // 使用新的 evaluate 签名
                    cellDisplayValue = cellRawValue; // 默认显示值等于原始值

                    // 存储需要合计/平均的原始数值 (在格式化之前)
                    if (currentRowNumerics != null && (tableConfig.getSumColumns().contains(column.getOutputKey()) || tableConfig.getAvgColumns().contains(column.getOutputKey()))) {
                        BigDecimal numericValue = getNumericValueForAggregation(cellRawValue);
                        if (numericValue != null) {
                            currentRowNumerics.put(column.getOutputKey(), numericValue);
                        } else {
                            // 如果某行需要聚合的列无法获取数值，可以选择记录警告，或在该行合计中忽略此值
                            log.trace("[{}] Could not extract numeric value for aggregation key '{}' from raw value '{}' at row {}",
                                    TRANSFORMER_TYPE, column.getOutputKey(), cellRawValue, rowIndex);
                        }
                    }

                    // 应用列格式化 (如果配置了) - 对 cellDisplayValue 进行格式化
                    if (column.getFormatterConfig() != null && cellDisplayValue != null) {
                        cellDisplayValue = formatCellValue(cellDisplayValue, column.getFormatterConfig());
                    }

                } catch (SpelEvaluationException e) {
                    log.error("[{}] SpEL evaluation failed for column '{}' (expr: '{}') at row {}. Error: {}",
                            TRANSFORMER_TYPE, column.getOutputKey(), column.getValueExpression(), rowIndex, e.getMessage());
                    cellDisplayValue = "EVAL_ERR"; // 最终显示错误标记
                } catch (Exception e) {
                    log.error("[{}] Error processing column '{}' at row {}. Error: {}",
                            TRANSFORMER_TYPE, column.getOutputKey(), rowIndex, e.getMessage(), e);
                    cellDisplayValue = "PROC_ERR"; // 最终显示错误标记
                }
                // 将最终的显示值放入输出行
                outputRow.put(column.getOutputKey(), cellDisplayValue);
            }
            outputTable.add(outputRow);
            if (currentRowNumerics != null && !currentRowNumerics.isEmpty()) { // 只添加包含有效数值的行
                numericValuesForTotal.add(currentRowNumerics);
            }
        }

        // 5. 添加合计行 (如果需要)
        if (tableConfig.isTotalEnabled()) {
            Map<String, Object> totalRowMap = calculateAndBuildTotalRow(numericValuesForTotal, tableConfig);
            if (totalRowMap != null) {
                outputTable.add(totalRowMap);
            }
        }

        return outputTable;
    }


    /**
     * 解析整体表格配置。
     */
    private TableBuildConfig parseConfig(JsonNode config) throws TransformationException {
        // ... (解析 columns 部分不变) ...
        if (config == null || !config.hasNonNull(CONFIG_COLUMNS_KEY) || !config.get(CONFIG_COLUMNS_KEY).isArray()) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: Missing or invalid '" + CONFIG_COLUMNS_KEY + "' (array).");
        }
        List<ColumnConfig> columns = new ArrayList<>();
        for (JsonNode colNode : config.get(CONFIG_COLUMNS_KEY)) {
            if (!colNode.isObject()) continue;
            String valueExpr = colNode.path(COL_VALUE_EXPR_KEY).asText(null);
            String outputKey = colNode.path(COL_OUTPUT_KEY).asText(null);
            if (!StringUtils.hasText(valueExpr) || !StringUtils.hasText(outputKey)) {
                throw new TransformationException(TRANSFORMER_TYPE, "Column config requires non-empty '" + COL_VALUE_EXPR_KEY + "' and '" + COL_OUTPUT_KEY + "'. Node: " + colNode);
            }
            JsonNode formatterConfig = colNode.path(COL_FORMATTER_KEY);
            columns.add(new ColumnConfig(valueExpr, outputKey, formatterConfig.isMissingNode() ? null : formatterConfig));
        }
        if (columns.isEmpty()) {
            throw new TransformationException(TRANSFORMER_TYPE, "Configuration error: '" + CONFIG_COLUMNS_KEY + "' array cannot be empty.");
        }

        // 解析合计行配置
        boolean totalEnabled = false;
        String totalLabelColumn = null;
        String totalLabelValue = "合计";
        List<String> sumColumns = Collections.emptyList();
        List<String> avgColumns = Collections.emptyList(); // <--- 初始化为空列表
        Map<String, JsonNode> totalFormatters = Collections.emptyMap();
        int avgScale = 2; // <--- 初始化默认值
        RoundingMode avgRoundingMode = RoundingMode.HALF_UP; // <--- 初始化默认值

        JsonNode totalRowNode = config.path(CONFIG_TOTAL_ROW_KEY);
        if (totalRowNode.isObject()) {
            totalEnabled = totalRowNode.path(TOTAL_ENABLED_KEY).asBoolean(false);
            if (totalEnabled) {
                totalLabelColumn = totalRowNode.path(TOTAL_LABEL_COL_KEY).asText(null);
                if (!StringUtils.hasText(totalLabelColumn)) {
                    throw new TransformationException(TRANSFORMER_TYPE, "Total row config requires '" + TOTAL_LABEL_COL_KEY + "' when enabled.");
                }
                totalLabelValue = totalRowNode.path(TOTAL_LABEL_VALUE_KEY).asText(totalLabelValue);
                try {
                    // 解析求和列
                    if (totalRowNode.hasNonNull(TOTAL_SUM_COLS_KEY) && totalRowNode.get(TOTAL_SUM_COLS_KEY).isArray()) {
                        sumColumns = objectMapper.readValue(totalRowNode.get(TOTAL_SUM_COLS_KEY).traverse(), new TypeReference<List<String>>() {
                        });
                    }
                    // --- 实现解析平均值列 ---
                    if (totalRowNode.hasNonNull(TOTAL_AVG_COLS_KEY) && totalRowNode.get(TOTAL_AVG_COLS_KEY).isArray()) {
                        avgColumns = objectMapper.readValue(totalRowNode.get(TOTAL_AVG_COLS_KEY).traverse(), new TypeReference<List<String>>() {
                        });
                    }
                    // ---------------------------
                    // 解析合计行格式化器
                    if (totalRowNode.hasNonNull(TOTAL_FORMATTERS_KEY) && totalRowNode.get(TOTAL_FORMATTERS_KEY).isObject()) {
                        totalFormatters = objectMapper.convertValue(totalRowNode.get(TOTAL_FORMATTERS_KEY), new TypeReference<Map<String, JsonNode>>() {
                        });
                    }
                    // --- 实现 解析 AVG 相关配置 ---
                    avgScale = totalRowNode.path(TOTAL_SCALE_KEY).asInt(avgScale); // 使用默认值
                    avgRoundingMode = parseRoundingMode(totalRowNode.path(TOTAL_ROUNDING_MODE_KEY).asText("HALF_UP")); // 使用默认值
                    // ---------------------------------
                } catch (IOException e) {
                    throw new TransformationException(TRANSFORMER_TYPE, "Error parsing total row config lists/maps.", e);
                }
            }
        }

        return new TableBuildConfig(columns, totalEnabled, totalLabelColumn, totalLabelValue, sumColumns, avgColumns, totalFormatters, avgScale, avgRoundingMode);
    }

    /**
     * 格式化单元格的值 (保持不变)
     */
    private Object formatCellValue(Object cellValue, JsonNode formatterConfig) {
        if (!formatterConfig.hasNonNull("pattern") || !formatterConfig.get("pattern").isTextual()) {
            log.warn("[{}] Invalid formatter config for cell: {}", TRANSFORMER_TYPE, formatterConfig);
            return cellValue; // 配置无效，返回原值
        }
        String pattern = formatterConfig.get("pattern").asText();
        boolean useGrouping = formatterConfig.path("useGrouping").asBoolean(true); // 可选

        if (cellValue instanceof Number) {
            try {
                DecimalFormat df = new DecimalFormat(pattern);
                df.setGroupingUsed(useGrouping);
                // 可以考虑从配置中获取 RoundingMode
                // df.setRoundingMode(RoundingMode.HALF_UP);
                return df.format(cellValue);
            } catch (IllegalArgumentException e) {
                log.error("[{}] Invalid number format pattern '{}' in column config. Value: {}", TRANSFORMER_TYPE, pattern, cellValue, e);
                return "FMT_ERR"; // 返回错误标记
            }
        }  else if (cellValue instanceof Date) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(pattern);
                return formatter.format(cellValue);
            } catch (IllegalArgumentException e) {
                log.error("[{}] Invalid date format pattern '{}' in column config. Value: {}", TRANSFORMER_TYPE, pattern, cellValue, e);
                return "FMT_ERR";
            }
        } else {
            log.trace("[{}] Cell value is not a number, skipping formatting. Type: {}", TRANSFORMER_TYPE, cellValue.getClass().getName());
            return cellValue; // 非数字不格式化
        }
    }

    /**
     * 尝试从单元格值获取 BigDecimal (保持不变)
     */
    private BigDecimal getNumericValueForAggregation(Object cellValue) {
        if (cellValue instanceof BigDecimal) {
            return (BigDecimal) cellValue;
        } else if (cellValue instanceof Number) {
            return new BigDecimal(cellValue.toString());
        } else if (cellValue instanceof String) {
            try {
                // 尝试去除可能的千分位和货币符号（需要更健壮的逻辑）
                String numericString = ((String) cellValue).replaceAll("[^\\d.-]", "");
                if (StringUtils.hasText(numericString)) {
                    return new BigDecimal(numericString);
                }
            } catch (NumberFormatException e) {
                log.trace("[{}] Cannot parse string '{}' to BigDecimal for aggregation.", TRANSFORMER_TYPE, cellValue);
            }
        }
        return null; // 无法获取数值
    }

    /**
     * 计算并构建合计行 Map 。
     */
    private Map<String, Object> calculateAndBuildTotalRow
    (List<Map<String, BigDecimal>> numericValuesForTotal, TableBuildConfig config) {
        if (!config.isTotalEnabled() || CollectionUtils.isEmpty(numericValuesForTotal)) {
            return null;
        }

        Map<String, Object> totalRow = new HashMap<>();
        totalRow.put(config.getTotalLabelColumn(), config.getTotalLabelValue()); // 设置标签

        // --- 计算求和列 ---
        for (String sumColKey : config.getSumColumns()) {
            BigDecimal sum = numericValuesForTotal.stream()
                    .map(rowNumerics -> rowNumerics.get(sumColKey)) // 获取该列的值
                    .filter(Objects::nonNull)                      // 过滤掉 null 值
                    .reduce(BigDecimal.ZERO, BigDecimal::add);    // 求和

            Object finalSumValue = formatAggregatedValue(sum, sumColKey, config); // 格式化结果
            totalRow.put(sumColKey, finalSumValue);
        }

        // --- 实现 计算平均值列 ---
        for (String avgColKey : config.getAvgColumns()) {
            List<BigDecimal> validValues = numericValuesForTotal.stream()
                    .map(rowNumerics -> rowNumerics.get(avgColKey))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            BigDecimal average = BigDecimal.ZERO; // 默认平均值为 0
            if (!validValues.isEmpty()) {
                BigDecimal sum = validValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                average = sum.divide(new BigDecimal(validValues.size()), config.getAvgScale(), config.getAvgRoundingMode());
            } else {
                log.warn("[{}] Cannot calculate average for column '{}' as no valid numeric values were found in collected data.", TRANSFORMER_TYPE, avgColKey);
            }

            Object finalAvgValue = formatAggregatedValue(average, avgColKey, config); // 格式化结果
            totalRow.put(avgColKey, finalAvgValue);
        }
        // ------------------------------

        log.debug("[{}] Calculated total row: {}", TRANSFORMER_TYPE, totalRow);
        return totalRow;
    }

    /**
     * 辅助方法：格式化聚合后的值（求和或平均）
     */
    private Object formatAggregatedValue(BigDecimal value, String columnKey, TableBuildConfig config) {
        if (config.getTotalFormatters().containsKey(columnKey)) {
            // 使用合计行特定的格式化器
            return formatCellValue(value, config.getTotalFormatters().get(columnKey));
        } else {
            // 如果没有特定格式化器，可以考虑查找列定义中的格式化器作为备选
            // ColumnConfig colDef = config.getColumns().stream().filter(c -> c.getOutputKey().equals(columnKey)).findFirst().orElse(null);
            // if (colDef != null && colDef.getFormatterConfig() != null) {
            //     return formatCellValue(value, colDef.getFormatterConfig());
            // }
            // 或者直接返回 BigDecimal
            return value;
        }
    }

    /**
     * 解析舍入模式字符串 (保持不变)
     */
    private RoundingMode parseRoundingMode(String modeStr) {
        try {
            return RoundingMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid rounding mode string: '{}'. Falling back to HALF_UP.", modeStr);
            return RoundingMode.HALF_UP;
        }
    }


    // --- 内部配置类 (TableBuildConfig 添加 avg 相关字段, ColumnConfig 不变) ---
    @RequiredArgsConstructor
    @lombok.Getter
    private static class TableBuildConfig {
        private final List<ColumnConfig> columns;
        private final boolean totalEnabled;
        private final String totalLabelColumn;
        private final String totalLabelValue;
        private final List<String> sumColumns;
        private final List<String> avgColumns; // 新增
        private final Map<String, JsonNode> totalFormatters;
        private final int avgScale;           // 新增
        private final RoundingMode avgRoundingMode; // 新增

        public boolean needsTotalOrAvg() {
            return totalEnabled && (!sumColumns.isEmpty() || !avgColumns.isEmpty());
        }
    }

    @RequiredArgsConstructor
    @lombok.Getter
    private static class ColumnConfig {
        private final String valueExpression;
        private final String outputKey;
        private final JsonNode formatterConfig;
    }
}