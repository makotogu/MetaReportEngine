package org.makotogu.metaReportEngine.shard.exception;

public class SpelEvaluationException extends RuntimeException {
    public SpelEvaluationException(String message) {
        super(message);
    }

    public SpelEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
