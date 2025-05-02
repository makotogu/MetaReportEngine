CREATE TABLE report_definition
(
    id            BIGSERIAL PRIMARY KEY,                       -- 主键ID, 使用 BIGSERIAL 实现自增
    report_id     VARCHAR(100) NOT NULL UNIQUE,                -- 报告唯一标识符 (业务定义), 唯一约束
    report_name   VARCHAR(255) NOT NULL,                       -- 报告名称 (展示用)
    template_path VARCHAR(512) NOT NULL,                       -- 关联的poi-tl模板文件路径
    description   TEXT         NULL,                           -- 报告描述信息
    version       VARCHAR(50)  NOT NULL DEFAULT '1.0',         -- 配置版本号
    status        VARCHAR(20)  NOT NULL DEFAULT 'ENABLED'      -- 配置状态 (ENABLED, DISABLED, ARCHIVED)
        CHECK (status IN ('ENABLED', 'DISABLED', 'ARCHIVED')), -- 增加检查约束确保状态值的有效性
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),         -- 创建时间, TIMESTAMPTZ 包含时区信息
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()          -- 最后更新时间 (应用层负责更新)
);

-- 添加注释
COMMENT ON TABLE report_definition IS '报告定义主表';
COMMENT ON COLUMN report_definition.id IS '主键ID, 自增';
COMMENT ON COLUMN report_definition.report_id IS '报告唯一标识符 (业务定义, e.g., CUST_RISK_ALERT_V1), 唯一';
COMMENT ON COLUMN report_definition.report_name IS '报告名称 (展示用)';
COMMENT ON COLUMN report_definition.template_path IS '关联的poi-tl模板文件路径 (相对或绝对)';
COMMENT ON COLUMN report_definition.description IS '报告描述信息';
COMMENT ON COLUMN report_definition.version IS '配置版本号';
COMMENT ON COLUMN report_definition.status IS '配置状态 (ENABLED, DISABLED, ARCHIVED)';
COMMENT ON COLUMN report_definition.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN report_definition.updated_at IS '最后更新时间 (带时区, 应用层负责更新)';


CREATE TABLE report_datasource
(
    id               BIGSERIAL PRIMARY KEY,                               -- 主键ID, 自增
    report_def_id    BIGINT       NOT NULL,                               -- 逻辑外键, 关联 report_definition.id (应用层保证)
    datasource_alias VARCHAR(100) NOT NULL,                               -- 数据源在本报告配置内的唯一别名
    query_type       VARCHAR(50)  NOT NULL,                               -- 查询类型 (e.g., mybatis, jdbc_template, http_api)
    query_ref        VARCHAR(512) NOT NULL,                               -- 查询引用 (e.g., MyBatis Mapper ID)
    param_mapping    JSONB        NULL,                                   -- 参数映射规则 (JSONB格式), e.g., {"custId": "context.customerId"}
    result_structure VARCHAR(50)  NOT NULL DEFAULT 'list_map'             -- 预期结果结构 (e.g., list_map, single_map, scalar)
        CHECK (result_structure IN ('list_map', 'single_map', 'scalar')), -- 增加检查约束
    description      TEXT         NULL,                                   -- 数据源描述信息
    execution_order  INT          NOT NULL DEFAULT 0,                     -- 执行顺序 (用于无显式依赖时的简单排序)
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),                 -- 创建时间 (带时区)
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),                 -- 最后更新时间 (应用层负责更新)

    UNIQUE (report_def_id, datasource_alias)                              -- 同一报告下的数据源别名必须唯一
);

-- 添加注释
COMMENT ON TABLE report_datasource IS '报告的数据源配置表';
COMMENT ON COLUMN report_datasource.id IS '主键ID, 自增';
COMMENT ON COLUMN report_datasource.report_def_id IS '逻辑外键, 关联 report_definition.id (应用层保证)';
COMMENT ON COLUMN report_datasource.datasource_alias IS '数据源在本报告配置内的唯一别名 (e.g., customer_info)';
COMMENT ON COLUMN report_datasource.query_type IS '查询类型 (e.g., mybatis, jdbc_template, http_api)';
COMMENT ON COLUMN report_datasource.query_ref IS '查询引用 (e.g., MyBatis Mapper ID)';
COMMENT ON COLUMN report_datasource.param_mapping IS '参数映射规则 (JSONB格式), 定义如何从上下文构造查询参数';
COMMENT ON COLUMN report_datasource.result_structure IS '预期结果结构 (e.g., list_map, single_map, scalar)';
COMMENT ON COLUMN report_datasource.description IS '数据源描述信息';
COMMENT ON COLUMN report_datasource.execution_order IS '执行顺序 (用于无显式依赖时的简单排序)';
COMMENT ON COLUMN report_datasource.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN report_datasource.updated_at IS '最后更新时间 (带时区, 应用层负责更新)';

-- 即使没有外键约束, 索引对于查询性能仍然非常重要
CREATE INDEX idx_report_datasource_report_def_id ON report_datasource (report_def_id);
ALTER TABLE report_datasource ADD COLUMN datasource_context VARCHAR(50) NULL;
COMMENT ON COLUMN report_datasource.datasource_context IS '用于选择业务数据源的上下文标识 (e.g., risk, crm)';

CREATE TABLE report_transformation_rule
(
    id                   BIGSERIAL PRIMARY KEY,               -- 主键ID, 自增
    report_def_id        BIGINT       NOT NULL,               -- 逻辑外键, 关联 report_definition.id (应用层保证)
    rule_alias           VARCHAR(100) NOT NULL,               -- 规则在本报告配置内的唯一别名
    transformer_type     VARCHAR(100) NOT NULL,               -- 转换器类型 (e.g., FORMATTER, UNIT_CONVERTER, CONDITIONAL_TEXT)
    input_refs           JSONB        NOT NULL,               -- 输入来源引用列表 (JSONB数组), e.g., ["customer_info"]
    config               JSONB        NULL,                   -- 转换器所需的具体配置 (JSONB格式), 结构由transformer_type决定
    output_variable_name VARCHAR(100) NOT NULL,               -- 此规则产生的输出变量名
    dependency_refs      JSONB        NULL,                   -- 显式声明依赖的其他规则别名 (JSONB数组)
    description          TEXT         NULL,                   -- 转换规则描述信息
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(), -- 创建时间 (带时区)
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(), -- 最后更新时间 (应用层负责更新)

    UNIQUE (report_def_id, rule_alias)                        -- 同一报告下的规则别名必须唯一
);

-- 添加注释
COMMENT ON TABLE report_transformation_rule IS '报告的数据转换规则配置表';
COMMENT ON COLUMN report_transformation_rule.id IS '主键ID, 自增';
COMMENT ON COLUMN report_transformation_rule.report_def_id IS '逻辑外键, 关联 report_definition.id (应用层保证)';
COMMENT ON COLUMN report_transformation_rule.rule_alias IS '规则在本报告配置内的唯一别名 (e.g., formatted_risk_score)';
COMMENT ON COLUMN report_transformation_rule.transformer_type IS '转换器类型 (e.g., FORMATTER, UNIT_CONVERTER, CONDITIONAL_TEXT)';
COMMENT ON COLUMN report_transformation_rule.input_refs IS '输入来源引用列表 (JSONB数组), 引用datasource_alias或rule_alias';
COMMENT ON COLUMN report_transformation_rule.config IS '转换器所需的具体配置 (JSONB格式), 结构由transformer_type决定';
COMMENT ON COLUMN report_transformation_rule.output_variable_name IS '此规则产生的输出变量名, 供后续规则或模板映射使用';
COMMENT ON COLUMN report_transformation_rule.dependency_refs IS '显式声明依赖的其他规则别名 (JSONB数组), 用于控制复杂执行顺序';
COMMENT ON COLUMN report_transformation_rule.description IS '转换规则描述信息';
COMMENT ON COLUMN report_transformation_rule.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN report_transformation_rule.updated_at IS '最后更新时间 (带时区, 应用层负责更新)';

-- 即使没有外键约束, 索引对于查询性能仍然非常重要
CREATE INDEX idx_report_transformation_rule_report_def_id ON report_transformation_rule (report_def_id);

CREATE TABLE report_template_mapping
(
    id              BIGSERIAL PRIMARY KEY,               -- 主键ID, 自增
    report_def_id   BIGINT       NOT NULL,               -- 逻辑外键, 关联 report_definition.id (应用层保证)
    template_tag    VARCHAR(255) NOT NULL,               -- poi-tl模板中的标签名 (e.g., {{customer_name}})
    data_source_ref VARCHAR(100) NOT NULL,               -- 数据来源引用 (指向一个rule_alias或datasource_alias)
    data_expression VARCHAR(512) NULL,                   -- 可选: 从数据源中提取/转换值的表达式 (e.g., SpEL)
    description     TEXT         NULL,                   -- 映射关系描述信息
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(), -- 创建时间 (带时区)
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(), -- 最后更新时间 (应用层负责更新)

    UNIQUE (report_def_id, template_tag)                 -- 同一报告下的模板标签映射必须唯一
);

-- 添加注释
COMMENT ON TABLE report_template_mapping IS '报告模板标签与数据源的映射表';
COMMENT ON COLUMN report_template_mapping.id IS '主键ID, 自增';
COMMENT ON COLUMN report_template_mapping.report_def_id IS '逻辑外键, 关联 report_definition.id (应用层保证)';
COMMENT ON COLUMN report_template_mapping.template_tag IS 'poi-tl模板中的标签名 (e.g., {{customer_name}}, {#main_table})';
COMMENT ON COLUMN report_template_mapping.data_source_ref IS '数据来源引用 (指向一个rule_alias或datasource_alias)';
COMMENT ON COLUMN report_template_mapping.data_expression IS '可选: 从数据源中提取/转换值的表达式 (e.g., SpEL: #data_source_ref.fieldName)';
COMMENT ON COLUMN report_template_mapping.description IS '映射关系描述信息';
COMMENT ON COLUMN report_template_mapping.created_at IS '创建时间 (带时区)';
COMMENT ON COLUMN report_template_mapping.updated_at IS '最后更新时间 (带时区, 应用层负责更新)';

-- 即使没有外键约束, 索引对于查询性能仍然非常重要
CREATE INDEX idx_report_template_mapping_report_def_id ON report_template_mapping (report_def_id);