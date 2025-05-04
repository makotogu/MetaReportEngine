# MetaReportEngine: 元数据驱动的 Word 报告生成引擎

**MetaReportEngine** 是一个基于 Java 和 Spring Boot 构建的强大而灵活的引擎，旨在根据动态数据自动生成复杂的 Microsoft Word (.docx) 报告。它通过采用**元数据驱动**的方法，解决了传统硬编码报告生成流程中常见的低效和错误问题。

告别为每个报告变体编写重复的 Java 代码！在数据库中定义您的报告结构、数据源、转换逻辑和模板映射，让引擎处理剩下的事情。

## ✨ 核心特性

*   **元数据驱动:** 通过数据库配置声明式地定义报告生成逻辑，而非硬编码。
*   **灵活的数据获取:**
    *   与 **MyBatis** 无缝集成以进行数据检索。
    *   支持**多数据源**，可根据配置 (`datasource_context`) 进行动态路由。
    *   (已规划/实现) 支持从配置库读取 **动态 SQL** 语句，并使用命名参数安全执行。
*   **强大的转换层:**
    *   采用**策略模式 (Strategy Pattern)** 和 Spring 自动发现机制，实现可扩展的数据转换器。
    *   内置常用转换器 (Transformer):
        *   **Formatter:** 使用模式格式化数字、日期等。
        *   **Aggregator:** 对数据列表执行 SUM, AVG, COUNT 操作。
        *   **UnitConverter:** 基于阈值自动转换单位 (例如 元 -> 万元 -> 亿元)。
        *   **ConditionalText:** 基于 SpEL 条件动态生成文本块，支持简单的变量替换。
        *   **TableBuilder:** 从列表数据构建复杂表格，支持列计算 (SpEL)、格式化和**合计/汇总行**。
        *   **(可选) SpelEvaluator:** 执行通用的 SpEL 表达式。
*   **健壮的执行流程:**
    *   **DAG 执行计划器:** 基于数据 (`input_refs`) 和显式 (`dependency_refs`) 依赖，通过拓扑排序自动确定转换规则的正确执行顺序，并检测循环依赖。
    *   **SpEL 集成:** 利用 Spring Expression Language (SpEL) 实现动态参数映射、条件逻辑和计算值（已考虑安全性）。
*   **Word 模板渲染:** 使用优秀的 [**poi-tl**](http://deepoove.com/poi-tl/) 库填充 `.docx` 模板。支持标准变量替换、列表/表格迭代（`{#list}` 或 RenderPolicy）、条件块（`{{?flag}}`）。
*   **易于扩展:** 无需修改核心引擎代码，即可轻松添加新的自定义 `Transformer` 实现或支持新的 `DatasourceExecutor` 类型。
*   **缓存机制:** 内置报告配置缓存 (Caffeine)，提高性能。

## 🏛️ 架构概览

MetaReportEngine 遵循分层架构，以确保清晰度和可维护性：
- API 层 (api): 暴露 REST 端点，处理外部请求。
- 核心服务层 (core): 负责整个生成流程的编排调度。
- 元数据层 (metadata): 管理配置元数据的加载、缓存和访问。
- 数据访问层 (DAL) (datasource): 从业务数据库获取原始数据，支持多数据源路由和动态 SQL。
- 转换层 (transformer): 执行具体的数据转换和业务逻辑计算。
- 渲染层 (rendering): 调用 poi-tl 生成最终 Word 文档。
- 共享层 (shared): 包含跨层使用的工具类和自定义异常。

## 🛠️ 技术栈
- Java: 8+
- 框架: Spring Boot 2.7.x
- 数据库: openGauss / PostgreSQL (推荐 9.2+ 支持 JSONB)
- ORM: MyBatis 3 / mybatis-spring-boot-starter
- Word 引擎: poi-tl 1.10.x
- 表达式语言: Spring Expression Language (SpEL)
- 缓存: Caffeine
- JSON: Jackson
- 构建: Maven
- 工具: Lombok

## ▶️ 使用方法 (API)
- 通过发送 POST 请求触发报告生成：
  - 接口: POST /reports/{reportId}/generate
  - 路径参数: {reportId} - 要生成的报告的唯一业务 ID。
  - 请求体 (可选): JSON 对象，代表初始的 executionContext。对象中的键值对可在 SpEL 中通过 #context['key'] 访问。
``` JSON
{
  "customerId": "CUST-999",
  "reportGenDate": "2023-11-15"
}
```
