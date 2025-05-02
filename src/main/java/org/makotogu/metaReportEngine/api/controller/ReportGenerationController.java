package org.makotogu.metaReportEngine.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.core.service.ReportGenerationService;
import org.makotogu.metaReportEngine.shard.exception.RenderingException;
import org.makotogu.metaReportEngine.shard.exception.ReportConfNotFoundException;
import org.makotogu.metaReportEngine.shard.exception.ReportGenerationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ReportGenerationController {

    private final ReportGenerationService reportGenerationService;

    @PostMapping("/{reportId}/generate")
    public ResponseEntity<byte[]> generateReport(
            @PathVariable String reportId,
            @RequestBody(required = false) Map<String, Object> context) {
        try {
            Map<String, Object> executionContext = (context == null) ? new HashMap<>() : new HashMap<>(context);
            byte[] reportBytes = reportGenerationService.generateReport(reportId, executionContext);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            // 建议生成一个更具体的文件名
            String filename = reportId + "_" + System.currentTimeMillis() + ".docx";
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(reportBytes.length);

            return new ResponseEntity<>(reportBytes, headers, HttpStatus.OK);

        } catch (ReportConfNotFoundException e) {
            log.warn("Report configuration not found via API for reportId: {}", reportId, e);
            // 返回 404 Not Found
            return ResponseEntity.notFound().build();
        } catch (RenderingException e) {
            log.error("Rendering failed via API for reportId: {}", reportId, e);
            // 返回 500 Internal Server Error 或其他合适的错误码
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Rendering failed: " + e.getMessage()).getBytes());
        } catch (ReportGenerationException e) {
            log.error("Generation failed via API for reportId: {}", reportId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Generation failed: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            log.error("Unexpected error via API for reportId: {}", reportId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Unexpected error").getBytes());
        }
    }
}
