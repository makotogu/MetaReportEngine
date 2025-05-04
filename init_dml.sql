-- MetaReportEngine - 初始化测试数据脚本
-- 场景: 迷你客户摘要报告 (MINI_CUST_SUMMARY_V1)

-- 清理旧数据 (可选, 如果需要重复执行脚本以保证幂等性)
DELETE FROM report_template_mapping WHERE report_def_id = (SELECT id FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1');
DELETE FROM report_transformation_rule WHERE report_def_id = (SELECT id FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1');
DELETE FROM report_datasource WHERE report_def_id = (SELECT id FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1');
DELETE FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1';

-- 1. 插入报告定义 (report_definition)
-- 假设此条记录生成的 id 为 1
INSERT INTO report_definition (report_id, report_name, template_path, description, version, status)
VALUES ('MINI_CUST_SUMMARY_V1', '迷你客户摘要 V1', '/templates/mini_summary_v1.docx', '一个简单的客户摘要报告，包含姓名和格式化总贷款额', '1.0', 'ENABLED');

-- 获取刚插入的 report_definition 的 id (如果需要动态获取)
-- 请根据您的实际情况调整或确认 id 为 1
-- SELECT id INTO @report_def_id FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1';

-- 2. 插入数据源配置 (report_datasource) - 关联 id=1
--    数据源1: 获取客户姓名
INSERT INTO report_datasource (report_def_id, datasource_alias, query_type, query_ref, param_mapping, result_structure, description)
VALUES (
    1, -- 关联 report_definition.id = 1
    'cust_name_ds',
    'mybatis',
    'com.bank.mapper.CustomerMapper.getCustomerName',
    '{"custId": "context.customerId"}'::jsonb, -- 参数映射: 从上下文取 customerId
    'scalar',                                   -- 预期结果: 单个值
    '获取指定客户的姓名'
);

--    数据源2: 获取客户的贷款列表 (用于计算总额)
INSERT INTO report_datasource (report_def_id, datasource_alias, query_type, query_ref, param_mapping, result_structure, description)
VALUES (
    1, -- 关联 report_definition.id = 1
    'cust_loans_ds',
    'mybatis',
    'com.bank.mapper.LoanMapper.getActiveLoans',
    '{"customerId": "context.customerId"}'::jsonb, -- 参数映射: 从上下文取 customerId
    'list_map',                                    -- 预期结果: Map列表 (每笔贷款一个Map)
    '获取指定客户的活跃贷款列表 (包含amount字段)'
);

-- 3. 插入转换规则配置 (report_transformation_rule) - 关联 id=1
--    规则1: 计算贷款总额
INSERT INTO report_transformation_rule (report_def_id, rule_alias, transformer_type, input_refs, config, output_variable_name, description)
VALUES (
    1, -- 关联 report_definition.id = 1
    'total_loan_rule',
    'AGGREGATOR',                               -- 使用聚合转换器
    '["cust_loans_ds"]'::jsonb,                 -- 输入依赖: cust_loans_ds 数据源
    '{"field": "amount", "function": "SUM"}'::jsonb, -- 配置: 对输入列表中的amount字段求和
    'total_amount_raw',                         -- 输出变量名: total_amount_raw
    '计算客户贷款总额'
);

--    规则2: 格式化贷款总额
INSERT INTO report_transformation_rule (report_def_id, rule_alias, transformer_type, input_refs, config, output_variable_name, description)
VALUES (
    1, -- 关联 report_definition.id = 1
    'formatted_loan_rule',
    'FORMATTER',                                -- 使用格式化转换器
    '["total_loan_rule"]'::jsonb,               -- 输入依赖: total_loan_rule 的输出
    '{"pattern": "#,##0.00 元"}'::jsonb,        -- 配置: 格式化模式
    'final_formatted_total',                    -- 输出变量名: final_formatted_total
    '将贷款总额格式化为带单位的字符串'
);

-- 4. 插入模板映射配置 (report_template_mapping) - 关联 id=1
--    映射1: 客户姓名
INSERT INTO report_template_mapping (report_def_id, template_tag, data_source_ref, description)
VALUES (
    1, -- 关联 report_definition.id = 1
    '{{customer_name}}',                        -- Word 模板中的标签
    'cust_name_ds',                             -- 数据来源: cust_name_ds 数据源的输出
    '将客户姓名填充到模板'
);

--    映射2: 格式化后的贷款总额
INSERT INTO report_template_mapping (report_def_id, template_tag, data_source_ref, description)
VALUES (
    1, -- 关联 report_definition.id = 1
    '{{formatted_loan_total}}',                 -- Word 模板中的标签
    'final_formatted_total',                      -- 数据来源: formatted_loan_rule 规则的输出
    '将格式化后的贷款总额填充到模板'
);


-- (可选) 插入另一个稍微复杂点的报告定义，用于测试多报告配置加载
/*
INSERT INTO report_definition (report_id, report_name, template_path, description, version, status)
VALUES ('ANOTHER_REPORT_V1', '另一个报告 V1', '/templates/another_report_v1.docx', '用于测试的第二个报告', '1.0', 'ENABLED');

-- 假设上面插入的 id 为 2
INSERT INTO report_datasource (report_def_id, datasource_alias, query_type, query_ref, result_structure)
VALUES (2, 'some_other_data', 'mybatis', 'com.bank.mapper.OtherMapper.getData', 'list_map');

INSERT INTO report_template_mapping (report_def_id, template_tag, data_source_ref)
VALUES (2, '{{some_tag}}', 'some_other_data');
*/

-- 检查插入结果 (可选)
SELECT * FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1';
SELECT * FROM report_datasource WHERE report_def_id = (SELECT id FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1');
SELECT * FROM report_transformation_rule WHERE report_def_id = (SELECT id FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1');
SELECT * FROM report_template_mapping WHERE report_def_id = (SELECT id FROM report_definition WHERE report_id = 'MINI_CUST_SUMMARY_V1');

COMMIT; -- 如果需要的话



-- MetaReportEngine - 添加 FormatterTransformer 测试数据
-- 场景: 扩展迷你客户摘要报告 (MINI_CUST_SUMMARY_V1, report_def_id = 1)

-- 3. 添加新的转换规则配置 (report_transformation_rule)

--    规则3: 格式化报告生成日期 (假设日期在上下文中以 'reportGenDate' 提供)
INSERT INTO report_transformation_rule (report_def_id, rule_alias, transformer_type, input_refs, config, output_variable_name, description)
VALUES (
           1, -- 关联 report_definition.id = 1
           'format_report_date_rule',
           'FORMATTER',                                  -- 使用格式化转换器
           '["context.reportGenDate"]'::jsonb,           -- 输入依赖: 从执行上下文获取 reportGenDate
           '{"pattern": "yyyy年MM月dd日"}'::jsonb,       -- 配置: 日期格式化模式
           'final_formatted_report_date',                -- 输出变量名
           '格式化报告生成日期'
       );
-- 注意: 'context.reportGenDate' 引用的是 executionContext 中 key 为 "reportGenDate" 的值。
-- 在调用 generateReport 时需要传入这个值，例如: Map.of("reportGenDate", LocalDate.now())


--    规则4: 格式化总贷款额为纯数字字符串 (依赖之前的 total_loan_rule)
INSERT INTO report_transformation_rule (report_def_id, rule_alias, transformer_type, input_refs, config, output_variable_name, description)
VALUES (
           1, -- 关联 report_definition.id = 1
           'numeric_loan_total_rule',
           'FORMATTER',                                  -- 使用格式化转换器
           '["total_loan_rule"]'::jsonb,                 -- 输入依赖: total_loan_rule 的输出 (total_amount_raw)
           '{"pattern": "#,##0.00"}'::jsonb,             -- 配置: 数字格式化模式 (无单位)
           'final_numeric_loan_total',                   -- 输出变量名
           '将贷款总额格式化为带千分位的纯数字字符串'
       );


-- 4. 添加新的模板映射配置 (report_template_mapping)

--    映射3: 格式化后的报告日期
INSERT INTO report_template_mapping (report_def_id, template_tag, data_source_ref, description)
VALUES (
           1, -- 关联 report_definition.id = 1
           '{{report_date_formatted}}',                 -- Word 模板中的新标签
           'final_formatted_report_date',                   -- 数据来源: format_report_date_rule 的输出
           '将格式化后的报告日期填充到模板'
       );

--    映射4: 纯数字格式的贷款总额
INSERT INTO report_template_mapping (report_def_id, template_tag, data_source_ref, description)
VALUES (
           1, -- 关联 report_definition.id = 1
           '{{loan_total_numeric}}',                   -- Word 模板中的新标签
           'final_numeric_loan_total',                  -- 数据来源: numeric_loan_total_rule 的输出
           '将纯数字格式的贷款总额填充到模板'
       );

-- 检查新插入的数据 (可选)
SELECT * FROM report_transformation_rule WHERE report_def_id = 1 AND rule_alias IN ('format_report_date_rule', 'numeric_loan_total_rule');
SELECT * FROM report_template_mapping WHERE report_def_id = 1 AND template_tag IN ('{{report_date_formatted}}', '{{loan_total_numeric}}');

COMMIT; -- 如果需要的话


-- 找到 formatted_loan_rule (假设 report_def_id = 1)
UPDATE report_transformation_rule
SET input_refs = '["total_amount_raw"]'::jsonb -- 将输入引用修改为前一个规则的输出变量名
WHERE report_def_id = 1 AND rule_alias = 'formatted_loan_rule';

-- 同时，也需要修改依赖 total_loan_rule 的 numeric_loan_total_rule
UPDATE report_transformation_rule
SET input_refs = '["total_amount_raw"]'::jsonb -- 将输入引用修改为前一个规则的输出变量名
WHERE report_def_id = 1 AND rule_alias = 'numeric_loan_total_rule';

COMMIT; -- 如果需要


UPDATE report_transformation_rule
SET
    transformer_type = 'UNIT_CONVERTER',
    -- 注意 input_refs 仍然是 'total_amount_raw'
    config = '{
      "thresholds": [10000, 100000000],
      "units": ["元", "万元", "亿元"],
      "precision": 2,
      "template": "{{value}} {{unit}}",
      "roundingMode": "HALF_UP",
      "useGrouping": true
    }'::jsonb,
    output_variable_name = 'final_formatted_total_with_unit', -- 改个新名字避免冲突
    description = '使用单位转换器格式化总贷款额'
WHERE report_def_id = 1 AND rule_alias = 'formatted_loan_rule'; -- 或者用一个新的 rule_alias

UPDATE report_template_mapping
SET
    data_source_ref = 'final_formatted_total_with_unit',
    template_tag = '{{formatted_loan_total_unit}}'
WHERE report_def_id = 1 AND template_tag = '{{formatted_loan_total}}';


-- 规则: 根据总贷款额判断风险提示语
INSERT INTO report_transformation_rule (report_def_id, rule_alias, transformer_type, input_refs, config, output_variable_name, description)
VALUES (
           1,
           'risk_level_text_rule',
           'CONDITIONAL_TEXT',
           '["total_amount_raw"]'::jsonb, -- 依赖总贷款额
           '{
             "condition": "#input0 > 100000",
             "trueTemplate": "警告：客户 {{cust_name_ds}} 贷款总额 {{final_formatted_total_with_unit}} 较高，请重点关注！",
             "falseTemplate": "客户 {{cust_name_ds}} 贷款情况正常。"
           }'::jsonb,   -- 条件: 总额大于 10 万 (#input0 引用第一个输入)  -- 条件为真时的模板 (包含变量替换)  -- 条件为假时的模板
           'final_risk_text', -- 输出变量名
           '根据贷款总额生成风险提示语'
       );

-- 别忘了添加 template_mapping
INSERT INTO report_template_mapping (report_def_id, template_tag, data_source_ref)
VALUES (1, '{{risk_warning}}', 'final_risk_text'); -- 假设模板中有 {{risk_warning}}


--    规则5: 使用 TableBuilder 构建贷款明细表格数据
INSERT INTO report_transformation_rule (report_def_id, rule_alias, transformer_type, input_refs, config, output_variable_name, description)
VALUES (
           1, -- 关联 report_definition.id = 1
           'loan_table_builder_rule',      -- 规则别名 (可以与之前的例子相同，只要 report_def_id 不同即可)
           'TABLE_BUILDER',                -- 使用 TableBuilder
           '["cust_loans_ds"]'::jsonb,      -- 输入依赖: 原始贷款列表 (cust_loans_ds 已在之前定义)
           -- 配置 JSON (与上个例子类似)
           '{
             "columns": [
               {
                 "valueExpression": "#row.id",
                 "outputKey": "loanId"
               },
               {
                 "valueExpression": "#row.loanType",
                 "outputKey": "type"
               },
               {
                 "valueExpression": "#row.amount / 10000",
                 "outputKey": "amountWan",
                 "formatter": { "pattern": "#,##0.00", "useGrouping": true }
               },
               {
                 "valueExpression": "#row.issueDate",
                 "outputKey": "issueDate",
                 "formatter": { "pattern": "yyyy-MM-dd" }
               }
             ],
             "totalRow": {
               "enabled": true,
               "labelColumn": "loanId",
               "labelValue": "合计",
               "sumColumns": ["amountWan"],
               "formatters": {
                 "amountWan": { "pattern": "#,##0.00", "useGrouping": true }
               }
             }
           }'::jsonb,
           'final_loan_detail_table_data', -- 输出变量名 (区别于之前的 total_amount_raw 等)
           '构建贷款明细表格数据并添加合计行 (用于迷你摘要)'
       )
ON CONFLICT (report_def_id, rule_alias) DO UPDATE SET -- 如果 rule_alias 已存在则更新
                                                      transformer_type = EXCLUDED.transformer_type,
                                                      input_refs = EXCLUDED.input_refs,
                                                      config = EXCLUDED.config,
                                                      output_variable_name = EXCLUDED.output_variable_name,
                                                      description = EXCLUDED.description,
                                                      updated_at = now();


-- 4. 新增一个模板映射配置 (report_template_mapping) - 关联 id=1

--    映射5: 表格数据
INSERT INTO report_template_mapping (report_def_id, template_tag, data_source_ref)
VALUES (
           1, -- 关联 report_definition.id = 1
           '{#loan_detail_table}',                   -- Word 模板中的新表格标签
           'loan_table_builder_rule'                 -- 数据来源: 新添加的 TableBuilder 规则的输出
       )
ON CONFLICT (report_def_id, template_tag) DO UPDATE SET -- 如果 template_tag 已存在则更新
                                                        data_source_ref = EXCLUDED.data_source_ref,
                                                        updated_at = now();

