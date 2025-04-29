## MetaReportEngine - 元数据驱动报告生成引擎设计文档

**版本:** 1.0

### 1. 背景与目标

**1.1 问题陈述**
当前基于 Java 实现的批量 Word 报告生成流程存在显著痛点：每新增或修改一份报告，都需要开发人员深入参与指标加工对接、编写 Word 模板、硬编码 Java 逻辑（查询、计算、格式化、条件判断、填充）等环节。此过程高度重复、耗时（约 2 周/新报告）、易出错（逻辑与模板未对齐、人为疏忽），导致开发效率低下、维护成本高昂、报告质量难以保证。

**1.2 目标**
设计并实现一个**元数据驱动**的报告生成引擎 (`MetaReportEngine`)，旨在：

- **解耦:** 将易变的报告业务逻辑与通用的引擎执行流程分离。
- **配置化:** 通过结构化的元数据（数据库配置）来定义报告的生成规则，取代硬编码。
- **提效:** 大幅缩短新增或修改报告的开发周期。
- **降错:** 减少因手动编码引入的错误，提高报告生成的一致性和准确性。
- **易维护:** 简化维护工作，使逻辑更清晰，问题定位更容易。

**1.3 核心收益**

- 开发效率提升 (预计 70%-90%)
- 报告质量与一致性提高
- 维护成本降低
- 业务逻辑显性化、易于理解和传承

### 2. 核心概念

- **元数据驱动 (Metadata-Driven):** 引擎的行为由外部的配置数据（元数据）决定，而不是写死在代码中。报告的“配方”存储在数据库里。
- **关注点分离 (Separation of Concerns):**
  - **引擎逻辑:** 负责通用的执行流程（加载配置、执行查询、应用转换、调用渲染）。
  - **报告配置 (元数据):** 定义特定报告的数据来源、处理规则、模板映射。
  - **表示层 (Word 模板):** 负责最终文档的布局和样式。

### 3. 系统架构

采用分层架构，部署为一个独立的 Spring Boot 应用 (`MetaReportEngine`)。

```
+-----------------------------------+      +-------------------------+
|  触发源 (存量系统/定时任务/MQ)    | ---> | API 层 (REST Controller)|  <-- 接收生成请求
+-----------------------------------+      +-------------------------+
                                                 | (调用)
                                                 v
+-------------------------------------------------------------------+
|                      引擎核心/服务层                              |  <-- 编排, 缓存
| +---------------------+  +-------------------+  +-----------------+ |
| | 元数据服务          |->| 编排逻辑          |->| 渲染服务 (PoiTl)| |
| | (含缓存: Caffeine) |  | (管理上下文, 顺序)|  | (调用poi-tl)    | |
| +---------------------+  +-------------------+  +-----------------+ |
+---------------------------------|----------------|-----------------+
         (加载配置)                 | (调用)         | (调用)
         v                        v                v
+--------------------------+  +----------------------+  +----------------------+
| 元数据访问层 (Metadata DAO)|  | 数据访问层 (DAL)     |  | 转换层               |
| - MyBatis Mapper (配置库)|  | - DatasourceExecutor |  | - Transformer 策略   |
+--------------------------+  |   (调用业务库MyBatis)|  |   (各种转换器实现)   |
                              +----------------------+  +----------------------+
         ^ (元数据)                 ^ (原始数据)             ^ (加工逻辑)
         |                          |                        |
+--------+--------------------------+------------------------+---------+
|                        元数据存储 (openGauss - 配置库)                 |
+----------------------------------------------------------------------+
         ^ (业务数据)
         |
+--------+-------------------------------------------------------------+
|                        业务数据存储 (业务库)                           |
+----------------------------------------------------------------------+
```

**各层职责简述:**

- **API 层:** 提供 RESTful 接口接收报告生成请求。
- **引擎核心/服务层:** 核心调度中心，负责加载配置、编排数据查询与转换流程、调用渲染服务、管理缓存。
- **元数据服务:** 负责从配置库加载、解析、缓存报告配置。
- **渲染服务:** 封装 `poi-tl` 调用逻辑。
- **元数据访问层 (Metadata DAO):** 使用 MyBatis 与**配置库**交互，读写元数据表。
- **数据访问层 (DAL):** 负责根据 `DataSource` 配置，调用**业务库**的 MyBatis Mapper 获取原始业务数据。
- **转换层:** 包含各种 `Transformer` 实现，执行具体的数据加工逻辑。
- **元数据存储:** openGauss 数据库，存储引擎的配置信息（4 张核心表）。
- **业务数据存储:** 报告所需原始数据所在的数据库（由 DAL 层访问）。

### 4. 数据模型 (openGauss - 配置库 DDL)

**核心原则:**

- 使用 `JSONB` 存储灵活的配置结构。
- 使用 `BIGSERIAL` 实现自增主键。
- **不使用数据库外键约束**，依赖应用层保证引用完整性。
- **不使用数据库触发器**更新 `updated_at`，由应用层负责。
- 为逻辑关联字段（如 `report_def_id`）创建索引以保证查询性能。

**DDL 脚本:**

**(1) `report_definition` (报告定义表)**

```sql
CREATE TABLE report_definition (
  id BIGSERIAL PRIMARY KEY,
  report_id VARCHAR(100) NOT NULL UNIQUE,
  report_name VARCHAR(255) NOT NULL,
  template_path VARCHAR(512) NOT NULL,
  description TEXT NULL,
  version VARCHAR(50) NOT NULL DEFAULT '1.0',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED', 'ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE report_definition IS '报告定义主表';
-- ... (字段注释省略，参见之前DDL)
```

- **作用:** 报告的唯一标识和基础档案。`report_id` 是所有其他配置关联的业务键。

**(2) `report_datasource` (数据源配置表)**

```sql
CREATE TABLE report_datasource (
  id BIGSERIAL PRIMARY KEY,
  report_def_id BIGINT NOT NULL, -- 逻辑外键 -> report_definition.id
  datasource_alias VARCHAR(100) NOT NULL,
  query_type VARCHAR(50) NOT NULL,
  query_ref VARCHAR(512) NOT NULL,
  param_mapping JSONB NULL,
  result_structure VARCHAR(50) NOT NULL DEFAULT 'list_map' CHECK (result_structure IN ('list_map', 'single_map', 'scalar')),
  description TEXT NULL,
  execution_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (report_def_id, datasource_alias)
);
CREATE INDEX idx_report_datasource_report_def_id ON report_datasource (report_def_id);
COMMENT ON TABLE report_datasource IS '报告的数据源配置表';
-- ... (字段注释省略)
```

- **作用:** 定义获取**原始业务数据**的方式。
- **`datasource_alias`:** 在报告内唯一标识此数据源，供后续引用。
- **`query_ref`:** 指向业务库的 MyBatis Mapper ID。实现了*调用哪个查询*的配置化。
- **`param_mapping` (JSONB):** 定义如何从上下文构造查询参数。实现了*如何传参*的配置化。

**(3) `report_transformation_rule` (数据转换规则表)**

```sql
CREATE TABLE report_transformation_rule (
  id BIGSERIAL PRIMARY KEY,
  report_def_id BIGINT NOT NULL, -- 逻辑外键 -> report_definition.id
  rule_alias VARCHAR(100) NOT NULL,
  transformer_type VARCHAR(100) NOT NULL,
  input_refs JSONB NOT NULL,
  config JSONB NULL,
  output_variable_name VARCHAR(100) NOT NULL,
  dependency_refs JSONB NULL,
  description TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (report_def_id, rule_alias)
);
CREATE INDEX idx_report_transformation_rule_report_def_id ON report_transformation_rule (report_def_id);
COMMENT ON TABLE report_transformation_rule IS '报告的数据转换规则配置表';
-- ... (字段注释省略)
```

- **作用:** 核心业务逻辑配置所在。定义数据加工步骤。
- **`rule_alias`:** 在报告内唯一标识此规则，用于引用其输出或声明依赖。
- **`transformer_type`:** 决定调用哪个 Java `Transformer` 实现。
- **`input_refs` (JSONB Array):** 定义此规则依赖的数据来源（`datasource_alias` 或其他 `rule_alias`），构建数据流。
- **`config` (JSONB):** 存储具体转换逻辑所需的参数和规则，其结构由 `transformer_type` 决定。**这是业务规则参数化的关键**。
- **`output_variable_name`:** 定义此规则输出结果在上下文中的变量名。
- **`dependency_refs` (JSONB Array):** 可选，用于处理复杂依赖关系。

**(4) `report_template_mapping` (模板标签映射表)**

```sql
CREATE TABLE report_template_mapping (
  id BIGSERIAL PRIMARY KEY,
  report_def_id BIGINT NOT NULL, -- 逻辑外键 -> report_definition.id
  template_tag VARCHAR(255) NOT NULL,
  data_source_ref VARCHAR(100) NOT NULL,
  data_expression VARCHAR(512) NULL,
  description TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (report_def_id, template_tag)
);
CREATE INDEX idx_report_template_mapping_report_def_id ON report_template_mapping (report_def_id);
COMMENT ON TABLE report_template_mapping IS '报告模板标签与数据源的映射表';
-- ... (字段注释省略)
```

- **作用:** 建立最终处理好的数据与 Word 模板标签之间的明确映射关系。
- **`template_tag`:** Word 模板中的 `{{tag}}` 或 `{#tag}`。
- **`data_source_ref`:** 指向提供数据的 `rule_alias` 或 `datasource_alias`。
- **`data_expression` (可选):** 用于从复杂数据源中提取部分数据（例如，使用 SpEL）。

### 5. 核心组件设计详解

**5.1 API 层 (`api.controller.ReportGenerationController`)**

- 提供 RESTful API (e.g., `POST /reports/{reportId}/generate`)。
- 接收 `reportId` 路径参数和包含执行上下文（如 `customerId`, `date` 等）的请求体 (JSON)。
- 调用 `ReportGenerationService.generateReport()`。
- 将返回的 `byte[]` 包装成文件下载响应 (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`)。

**5.2 引擎核心/服务层 (`core.service.ReportGenerationService`)**

- **职责:** 编排整个报告生成流程。
- **依赖:** `MetadataService`, `DatasourceExecutor`, `PoiTlRenderingService`, `Transformer` 工厂/注册表。
- **核心方法:** `generateReport(String reportId, Map<String, Object> initialContext)`
  1.  调用 `MetadataService` 加载并缓存 `ReportConfigurationDto`。
  2.  初始化内部执行上下文 `Map<String, Object> executionContext`，并合并 `initialContext`。
  3.  **依赖分析:** (需要实现) 解析 `TransformationRule` 的 `input_refs` 和 `dependency_refs`，构建执行计划 (DAG)，确定 `DataSource` 查询和 `TransformationRule` 执行的正确顺序。
  4.  **执行数据源查询:** 按计划遍历 `DataSource` 配置，调用 `DatasourceExecutor.execute()` 获取原始数据，将结果存入 `executionContext` (使用 `datasource_alias` 作为 key)。
  5.  **执行转换规则:** 按计划遍历 `TransformationRule` 配置：
      - 从 `executionContext` 获取 `input_refs` 指定的输入数据。
      - 根据 `transformer_type` 获取对应的 `Transformer` 实例。
      - 调用 `transformer.transform(inputs, config, context)` 执行转换。
      - 将转换结果（`output_variable_name` 和 值）存入 `executionContext`。
  6.  **准备渲染数据:** 根据 `TemplateMapping` 配置，从 `executionContext` 构建最终的 `Map<String, Object> renderData`。处理 `data_expression` (如果存在)。
  7.  调用 `PoiTlRenderingService.renderReport()` 进行渲染。
  8.  返回生成的 `byte[]`。
- **错误处理:** 在各步骤中捕获异常，记录详细日志，并向上抛出自定义异常（如 `ReportGenerationException`）。

**5.3 元数据处理 (`metadata.*`)**

- **`MetadataService`:**
  - 提供 `getReportConfiguration(String reportId)` 方法。
  - 内部调用 `Metadata DAO` 获取数据。
  - 使用 Caffeine 实现缓存，key 为 `reportId`，value 为组装好的 `ReportConfigurationDto`。
  - 处理配置未找到的情况 (`ConfigurationNotFoundException`)。
- **`Metadata DAO` (MyBatis Mappers):**
  - 提供对 4 张配置表的基本 CRUD (主要是 Read) 操作。
- **`ReportConfigurationDto`:** 一个 Java 对象，封装了从数据库加载的某个报告的所有配置信息，方便引擎核心层使用。

**5.4 数据访问层 (DAL - `datasource.*`)**

- **`DatasourceExecutor` (Interface/SPI):** 定义执行数据源查询的契约。
  - `Object execute(ReportDatasource datasourceConfig, Map<String, Object> executionContext);`
- **`MybatisDatasourceExecutor` (Implementation):**
  - **关键:** 需要配置并能够访问到**业务数据库**的 `SqlSessionFactory` 或 `SqlSessionTemplate`。这可能需要独立配置或从共享库获取。
  - 实现 `execute` 方法：
    - 解析 `datasourceConfig.param_mapping`，从 `executionContext` 提取参数值（可能需要 SpEL 支持）。
    - 构造传递给 MyBatis Mapper 方法的参数对象 (Map 或 POJO)。
    - 动态获取或直接调用 `datasourceConfig.query_ref` 指定的 MyBatis MappedStatement ID。
    - 执行查询并返回结果（根据 `result_structure` 可能是 `List<Map>`, `Map`, 或标量）。
    - 处理 MyBatis 执行异常。

**5.5 转换层 (`transformer.*`)**

- **`Transformer` (Interface):**
  - `Object transform(List<Object> inputs, JsonNode config, Map<String, Object> executionContext);` （或者类似签名）
- **`Transformer` 实现类 (策略模式):** 为每个 `transformer_type` 创建一个实现类。
  - **`FormatterTransformer`:** 解析 `config` 中的 `pattern`，使用 `DecimalFormat`, `SimpleDateFormat` 等进行格式化。
  - **`UnitConverterTransformer`:** 解析 `config` 中的 `thresholds`, `units`, `precision`, `template`，执行单位转换逻辑。
  - **`ConditionalTextTransformer`:**
    - 使用 SpEL 引擎安全地评估 `config.condition` 表达式（输入为 `inputs`）。
    - 根据结果选择 `config.true_template` 或 `config.false_template`。
    - 可能需要支持简单的模板变量替换（从 `inputs` 获取）。
    - 需要特殊处理复杂中文序列拼接逻辑（可能需要更复杂的 `config` 或专门的 `SequenceTextBuilder`）。
  - **`TableBuilderTransformer`:**
    - 解析 `config.columns` (header, value_expression, output_key, formatter)。
    - 遍历 `inputs` (通常是 `List<Map>`)。
    - 对每一行，根据 `value_expression` (可使用 SpEL) 计算或提取列值，应用格式化。
    - 构建输出的 `List<Map>` (key 为 `output_key`)。
    - 处理可选的合计行 (`config.totalRow`)。
  - **`AggregatorTransformer`:** 解析 `config.field`, `config.function`，对输入的集合执行聚合操作。
  - **`SpelEvaluatorTransformer`:** 解析 `config.expression`，使用 SpEL 对 `inputs` 进行计算，返回结果。
- **Transformer 注册与发现:** 使用 Spring 的 `@Component` + `@Qualifier`，或者工厂模式来管理和获取 `Transformer` 实例。
- **SpEL 使用:** 需要创建 `StandardEvaluationContext`，并可能限制其能力以防止安全风险。

**5.6 渲染层 (`rendering.service.PoiTlRenderingService`)**

- 封装 `XWPFTemplate.compile(templatePath).render(renderData)` 调用。
- 处理 `poi-tl` 相关异常。

### 6. Word 模板设计指南

- **简洁性:** 模板侧重布局和样式，避免复杂逻辑。
- **清晰映射:** 模板标签 (`{{tag}}`, `{#tag}`) 必须与 `report_template_mapping` 配置一一对应。
- **命名规范:** 标签名清晰、一致。
- **逻辑在引擎:** 条件判断、数据格式化等逻辑在转换规则中完成。
- **利用 Word 功能:** 使用 Word 样式、表格、页眉页脚等。
- **版本控制:** 模板文件纳入版本管理。

### 7. 技术栈总结

- **语言/框架:** Java 8, Spring Boot 2.7.18
- **数据库:** openGauss (兼容 PostgreSQL 9.2+)
- **数据访问:** MyBatis (配置库 + 业务库)
- **Word 模板:** poi-tl 1.10.0
- **JSON 处理:** Jackson
- **表达式语言:** Spring Expression Language (SpEL)
- **缓存:** Caffeine
- **日志:** SLF4j + Logback/Log4j2

### 8. 实施计划（建议步骤）

1.  **项目搭建与基础配置** (新工程)。
2.  **元数据访问层 (Metadata DAO) 与服务 (MetadataService)** 实现与测试 (含缓存)。
3.  **渲染层 (RenderingService)** 实现与测试。
4.  **引擎核心骨架 (ReportGenerationService)** 搭建，打通基本流程（使用模拟数据）。
5.  **API 层 (ReportGenerationController)** 实现，提供入口。
6.  **数据访问层 (DAL - DatasourceExecutor)** 实现，**重点关注与业务库 MyBatis 的集成**。
7.  **转换层 (Transformer)** 逐个实现核心转换器，并集成到引擎核心。
8.  **依赖分析与执行计划** 逻辑实现。
9.  **端到端测试与调优**。

### 9. 关键考虑与决策

- **元数据复杂度:** JSON 配置可能变得复杂，需要良好的设计、文档和可能的校验机制。未来可考虑配置 UI。
- **查询策略:** 采用 `query_ref` 指向 MyBatis Mapper 是对易用性、功能复用和性能的权衡。
- **错误处理:** 需要定义详细的错误处理策略（日志、异常类型、是否中断、默认值等）。
- **可观测性 (Observability):** 完善日志记录（使用 MDC 跟踪请求），未来可考虑加入 Metrics。
- **可扩展性 (Scalability):** 引擎本身设计为无状态，易于水平扩展。性能瓶颈可能在数据库查询或复杂转换。缓存能缓解元数据加载压力。
- **无数据库约束:** 应用层**必须**承担保证引用完整性的责任。相关代码逻辑需要健壮。
- **SpEL 安全:** 限制 SpEL 上下文的能力，防止潜在的安全风险。

### 10. 未来可能的增强

- 开发可视化配置界面 (CRUD 元数据)。
- 实现更复杂的 Transformer 类型（如图表生成、交叉表等）。
- 增加配置校验功能。
- 提供元数据版本管理和回滚功能。
- 支持更多数据源类型（如 HTTP API, Elasticsearch）。

### 11. 结论

MetaReportEngine 通过元数据驱动的方式，将报告生成的业务逻辑配置化，能够有效解决当前流程的痛点，显著提升开发效率、降低错误率并简化维护。虽然前期需要投入开发引擎本身，但长期收益巨大，特别适合报告需求多变、重复性高的场景。
