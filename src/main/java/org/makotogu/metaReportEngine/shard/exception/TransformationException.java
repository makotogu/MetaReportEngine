package org.makotogu.metaReportEngine.shard.exception;

public class TransformationException extends RuntimeException {
    public TransformationException(String message) {
        super(message);
    }

    public TransformationException(String message, Throwable cause) {
        super(message, cause);
    }

    // 可以添加更多构造函数，例如包含出错的规则别名等上下文信息
    public TransformationException(String ruleAlias, String message, Throwable cause) {
        super(String.format("Error executing transformation rule '%s': %s", ruleAlias, message), cause);
    }

    public TransformationException(String ruleAlias, String message) {
        super(String.format("Error executing transformation rule '%s': %s", ruleAlias, message));
    }
}
