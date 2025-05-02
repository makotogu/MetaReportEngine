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
    'formatted_loan_rule',                      -- 数据来源: formatted_loan_rule 规则的输出
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