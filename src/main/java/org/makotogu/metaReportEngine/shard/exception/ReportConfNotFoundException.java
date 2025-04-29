package org.makotogu.metaReportEngine.shard.exception;

public class ReportConfNotFoundException extends RuntimeException {

    public ReportConfNotFoundException(String reportId) {
        super( "Configuration not found for reportId: " + reportId);
    }


}
