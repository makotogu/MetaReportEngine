package org.makotogu.metaReportEngine.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.shard.exception.TransformationException;
import org.makotogu.metaReportEngine.transformer.spi.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 转换器执行器服务。
 * 负责管理所有 Transformer 实现，并根据类型查找和执行它们。
 */
@Service
@Slf4j
public class TransformerExecutor {

    private final List<Transformer> transformerList; // 注入所有实现了 Transformer 接口的 Spring Bean
    private Map<String, Transformer> transformerMap; // 以 transformerType 为 key 的注册表

    /**
     * 通过构造函数注入所有检测到的 Transformer Bean 列表。
     * @param transformerList Spring 自动注入的 Transformer 实例列表。
     */
    @Autowired
    public TransformerExecutor(List<Transformer> transformerList) {
        // 暂时只保存列表，Map 的构建推迟到 @PostConstruct
        this.transformerList = CollectionUtils.isEmpty(transformerList) ? Collections.emptyList() : transformerList;
    }

    /**
     * 在 Bean 初始化完成后，将注入的 Transformer 列表转换为 Map 注册表。
     * 这样做可以避免在构造函数中调用可能尚未完全初始化的 Bean 的方法。
     */
    @PostConstruct
    public void initializeTransformerMap() {
        this.transformerMap = this.transformerList.stream()
                .peek(t -> log.debug("Registering Transformer: type={}, class={}", t.getTransformerType(), t.getClass().getName()))
                // 使用 Collectors.toMap，处理潜在的 key 冲突 (理论上不应发生，如果发生说明配置有问题)
                .collect(Collectors.toMap(
                        Transformer::getTransformerType, // Key: 转换器类型
                        Function.identity(),           // Value: 转换器实例本身
                        (existing, replacement) -> {    // Merge function for duplicates
                            log.warn("Duplicate Transformer detected for type: '{}'. Existing: {}, Replacement: {}. Using existing.",
                                    existing.getTransformerType(), existing.getClass().getName(), replacement.getClass().getName());
                            return existing; // 保留第一个找到的，或者可以抛出异常
                        }
                ));
        log.info("Initialized TransformerExecutor with {} transformers for types: {}", transformerMap.size(), transformerMap.keySet());

        // 可选：检查是否有关键的 Transformer 未被加载
        // if (!transformerMap.containsKey("FORMATTER")) { log.error("Core transformer 'FORMATTER' not found!"); }
    }

    /**
     * 根据指定的类型执行相应的转换器。
     *
     * @param transformerType      要执行的转换器类型 (来自 report_transformation_rule.transformer_type)
     * @param inputs               输入数据列表 (来自 resolveInputs 的结果)
     * @param config               当前规则的配置 (来自 report_transformation_rule.config)
     * @param executionContext     当前的执行上下文 Map
     * @param ruleAlias            当前执行的规则别名 (用于错误报告)
     * @return 转换后的结果 Object
     * @throws TransformationException 如果找不到指定类型的转换器，或者转换过程中发生错误。
     */
    public Object executeTransformer(String transformerType, List<Object> inputs, JsonNode config, Map<String, Object> executionContext, String ruleAlias)
            throws TransformationException {

        // 1. 从注册表中查找 Transformer
        Transformer transformer = transformerMap.get(transformerType);

        // 2. 处理找不到 Transformer 的情况
        if (transformer == null) {
            log.error("No Transformer registered for type: {}", transformerType);
            // 使用包含 ruleAlias 的构造函数抛出异常
            throw new TransformationException(ruleAlias, "Unsupported transformer type: " + transformerType);
        }

        // 3. 执行找到的 Transformer
        try {
            log.debug("Executing Transformer [Type: {}, RuleAlias: {}]", transformerType, ruleAlias);
            // 调用具体 Transformer 实现的 transform 方法
            Object result = transformer.transform(inputs, config, executionContext);
            log.debug("Transformer [Type: {}, RuleAlias: {}] executed successfully.", transformerType, ruleAlias);
            return result;
        } catch (TransformationException te) {
            // 如果 transform 方法内部已经抛出了 TransformationException，直接重新抛出
            // 可以选择在这里再包装一层，添加更多上下文，但通常内部抛出的信息更具体
            log.error("Transformation failed during execution of rule '{}' (type: {})", ruleAlias, transformerType, te);
            throw te;
        } catch (Exception e) {
            // 捕获其他未预料的运行时异常，包装成 TransformationException
            log.error("Unexpected error during execution of rule '{}' (type: {}): {}", ruleAlias, transformerType, e.getMessage(), e);
            throw new TransformationException(ruleAlias, "Unexpected error during transformation: " + e.getMessage(), e);
        }
    }
}
