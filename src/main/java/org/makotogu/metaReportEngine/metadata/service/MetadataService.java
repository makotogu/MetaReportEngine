package org.makotogu.metaReportEngine.metadata.service;

import lombok.AllArgsConstructor;
import org.makotogu.metaReportEngine.metadata.dto.ReportConfigurationDto;
import org.makotogu.metaReportEngine.metadata.entity.ReportDataSource;
import org.makotogu.metaReportEngine.metadata.entity.ReportDefinition;
import org.makotogu.metaReportEngine.metadata.entity.ReportTemplateMapping;
import org.makotogu.metaReportEngine.metadata.entity.ReportTransformationRule;
import org.makotogu.metaReportEngine.metadata.persistence.ReportDataSourceMapper;
import org.makotogu.metaReportEngine.metadata.persistence.ReportDefinitionMapper;
import org.makotogu.metaReportEngine.metadata.persistence.ReportTemplateMappingMapper;
import org.makotogu.metaReportEngine.metadata.persistence.ReportTransformationRuleMapper;
import org.makotogu.metaReportEngine.shard.exception.ReportConfNotFoundException;
import org.makotogu.metaReportEngine.shard.util.CacheUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MetadataService 需要完成的具体任务如下:
 * <p>
 * 接收报告标识符:
 * 它的核心公共方法（例如 getReportConfiguration）需要接收一个 reportId 作为输入参数，以确定要加载哪个报告的配置。
 * 从数据访问层 (DAO) 加载原始配置数据:
 * 根据传入的 reportId，调用 ReportDefinitionMapper 查询对应的 ReportDefinition 记录。
 * 处理未找到的情况: 如果根据 reportId 找不到 ReportDefinition，必须抛出一个明确的异常（例如自定义的 ReportConfNotFoundException），告知调用方配置不存在。
 * 如果找到了 ReportDefinition，获取其主键 id (report_def_id)。
 * 使用获取到的 report_def_id，分别调用 ReportDatasourceMapper, ReportTransformationRuleMapper, ReportTemplateMappingMapper 查询该报告关联的所有数据源、转换规则和模板映射记录列表。
 * </p>
 * <p>
 * 数据聚合与转换 (组装 DTO):
 * 将从四个 Mapper 中获取到的原始实体对象（或对象列表）聚合起来。
 * 创建一个 ReportConfigurationDto 对象。
 * 将 ReportDefinition 实体的数据映射填充到 ReportConfigurationDto 的 definition 内部类中。
 * 遍历从 ReportDatasourceMapper 获取的实体列表，将每个实体的数据映射填充到 ReportConfigurationDto.DataSourceConfig 对象中，并将这些对象添加到 ReportConfigurationDto 的 dataSources 列表中。关键转换: 在此过程中，需要将数据库中存储的 param_mapping (JSONB/TEXT) 字符串解析为 JsonNode 对象（使用 Jackson ObjectMapper）。
 * 类似地，遍历 ReportTransformationRule 实体列表，映射到 ReportConfigurationDto.RuleConfig 列表。关键转换: 解析 input_refs (JSONB/TEXT) 为 List<String>，解析 config (JSONB/TEXT) 为 JsonNode，解析 dependency_refs (JSONB/TEXT) 为 List<String>。
 * 类似地，遍历 ReportTemplateMapping 实体列表，映射到 ReportConfigurationDto.MappingConfig 列表。
 * 处理潜在的解析错误:
 * 在将数据库中的 JSON 字符串解析为 JsonNode 或 List<String> 时，可能会遇到格式错误。MetadataService 需要妥善处理这些 JsonProcessingException，例如记录详细错误日志，并可能抛出一个指示配置错误的异常。
 * </p>
 * <p>
 * 实现缓存逻辑 (核心优化):
 * 缓存读取: 在执行第 2 步（从 DAO 加载）之前，首先尝试根据 reportId 从缓存（例如 Caffeine Cache）中获取 ReportConfigurationDto 对象。
 * 缓存命中: 如果在缓存中找到了有效的 ReportConfigurationDto 对象，则直接返回该对象，跳过后续的数据库查询和 DTO 组装步骤。
 * 缓存未命中: 如果缓存中没有找到，则执行第 2、3、4 步（从数据库加载、组装 DTO、处理错误）。
 * 缓存写入: 在成功组装 ReportConfigurationDto 对象后，将其放入缓存中，使用 reportId 作为键。
 * 缓存配置: 需要配置缓存策略（如最大容量、过期时间 TTL/TTI）。
 * </p>
 * <p>
 * 提供缓存失效机制 (可选但推荐):
 * 提供一个公共方法（例如 invalidateCache(String reportId)），允许外部（例如，配置管理接口）在配置发生变更时手动清除指定 reportId 的缓存，确保引擎下次能加载到最新的配置。
 * 返回组装好的 DTO:
 * 将最终组装完成并通过验证（或至少成功解析）的 ReportConfigurationDto 对象返回给调用方 (ReportGenerationService)。
 * </p>
 * 总结来说，MetadataService 的核心任务是： 接收 reportId -> 检查缓存 -> (缓存未命中) -> 查询各配置表 -> 校验基础数据 -> 解析JSON字段 -> 组装成ReportConfigurationDto -> 存入缓存 -> 返回 ReportConfigurationDto。 它封装了与配置数据获取和准备相关的所有复杂性，为上层服务提供了一个干净、一致且带有缓存优化的配置视图。
 */
@Service
@AllArgsConstructor
public class MetadataService {

    private final ReportDefinitionMapper reportDefinitionMapper;
    private final ReportDataSourceMapper reportDataSourceMapper;
    private final ReportTransformationRuleMapper reportTransformationRuleMapper;
    private final ReportTemplateMappingMapper reportTemplateMappingMapper;
    private final CacheUtil cacheUtil;

    public List<ReportDefinition> getAllReportDefinition() {
        return reportDefinitionMapper.getAllReportDefinition();
    }

    public ReportConfigurationDto getReportConfiguration(String reportId) {
        ReportDefinition reportDefinition = reportDefinitionMapper.getReportDefinitionById(reportId);
        if (reportDefinition != null) {
            // 获取 report_def_id
            Long reportDefId = reportDefinition.getId();
            // 首先尝试从 caffeine cache 中获取 ReportConfigurationDto
            ReportConfigurationDto reportConfigurationDto = cacheUtil.getReportConfig(reportDefId);
            if (reportConfigurationDto != null) {
                return reportConfigurationDto;
            }
            // 获取数据源配置
            List<ReportDataSource> reportDataSources = reportDataSourceMapper.getReportDataSourcesByReportDefId(reportDefId);
            // 获取数据转换规则配置
            List<ReportTransformationRule> transformationRules = reportTransformationRuleMapper.getTransformationRulesByReportDefId(reportDefId);
            // 获取模板映射配置
            List<ReportTemplateMapping> templateMappings = reportTemplateMappingMapper.getReportTemplateMappingsByReportDefId(reportDefId);
            // 数据组装
            reportConfigurationDto = new ReportConfigurationDto();
            reportConfigurationDto.setDefinition(reportDefinition);
            reportConfigurationDto.setDataSources(reportDataSources);
            reportConfigurationDto.setTransformationRules(transformationRules);
            reportConfigurationDto.setTemplateMappings(templateMappings);
            cacheUtil.putReportConfig(reportDefId, reportConfigurationDto);
            return reportConfigurationDto;
        } else {
            throw new ReportConfNotFoundException(reportId);
        }
    }
}
