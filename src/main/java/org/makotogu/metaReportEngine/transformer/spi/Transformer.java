package org.makotogu.metaReportEngine.transformer.spi;

import com.fasterxml.jackson.databind.JsonNode;
import org.makotogu.metaReportEngine.shard.exception.TransformationException;


import java.util.List;
import java.util.Map;

/**
 * Transformer 接口定义了所有数据转换器的契约。
 * 每个实现负责处理一种特定的转换逻辑（由 transformer_type 标识）。
 */
public interface Transformer {

    /**
     * 获取此转换器能够处理的类型标识符。
     * 这个字符串必须与数据库 report_transformation_rule 表中的 transformer_type 字段值完全匹配。
     *
     * @return 转换器类型标识符 (例如 "FORMATTER", "AGGREGATOR", "TABLE_BUILDER")
     */
    String getTransformerType();

    /**
     * 执行数据转换的核心方法。
     *
     * @param inputs           输入数据列表。列表中的元素是根据规则配置中的 input_refs 从 executionContext 中获取的数据。
     *                         顺序通常与 input_refs 中的顺序对应，但具体实现不应强依赖顺序，除非业务逻辑明确要求。
     * @param config           当前转换规则的具体配置信息 (来自 report_transformation_rule 表的 config 字段，已解析为 JsonNode)。
     *                         实现类需要根据自身逻辑解析这个 JsonNode 获取所需参数。如果某转换器不需要配置，此参数可能为 null 或 JsonNode.isNull()。
     * @param executionContext 当前报告生成的完整执行上下文 Map。包含初始参数、所有已执行的数据源和转换规则的输出。
     *                         转换器可以通过它获取其他非直接输入的数据，但不建议过度依赖，以保持规则的独立性。修改此 Map 需要谨慎。
     * @return 转换后的结果。结果的类型取决于具体的转换逻辑（可能是 String, Number, List<Map<String, Object>>, Boolean 等）。
     *         这个结果将被放入 executionContext 中，使用规则配置的 output_variable_name 作为键。
     * @throws TransformationException 如果在转换过程中发生任何错误（例如，配置无效、输入数据不合规、计算错误等）。
     */
    Object transform(List<Object> inputs, JsonNode config, Map<String, Object> executionContext) throws TransformationException;
}
