package org.makotogu.metaReportEngine.shard.exception;

public class DatasourceExecutionException extends RuntimeException {
    public DatasourceExecutionException(String message) {
        super(message);
    }
    public DatasourceExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
