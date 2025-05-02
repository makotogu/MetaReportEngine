package org.makotogu.metaReportEngine.rendering.service;

import com.deepoove.poi.XWPFTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.makotogu.metaReportEngine.shard.exception.RenderingException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiTlRenderingService {

    private final ResourceLoader resourceLoader;

    public byte[] renderReport(String templatePath, Map<String, Object> renderData) throws RenderingException {
        log.debug("Rendering report from template: {}", templatePath);
        Resource resource = resourceLoader.getResource(templatePath);

        if (!resource.exists()) {
            log.error("Template resource not found: {}", templatePath);
            throw new RenderingException("Template not found: " + templatePath);
        }

        try (InputStream inputStream = resource.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // 编译模板 (未来可考虑传入 Configure 对象进行定制)
            XWPFTemplate template = XWPFTemplate.compile(inputStream);

            // 渲染数据
            template.render(renderData);

            // 将渲染结果写入字节数组输出流并关闭 poi-tl 资源
            template.writeAndClose(baos);

            byte[] reportBytes = baos.toByteArray();
            log.debug("Report rendered successfully, size: {} bytes", reportBytes.length);
            return reportBytes;

        } catch (IOException e) {
            log.error("IO error during template loading or rendering for path: {}", templatePath, e);
            throw new RenderingException("IO error during rendering for template: " + templatePath, e);
        } catch (Exception e) { // 捕获其他可能的 poi-tl 运行时异常
            log.error("Error rendering report with poi-tl for path: {}", templatePath, e);
            throw new RenderingException("Rendering failed for template: " + templatePath, e);
        }
    }

    public void renderReport(String templatePath, Map<String, Object> renderData, OutputStream outputStream) throws RenderingException {
        log.debug("Rendering report from template: {} to output stream", templatePath);
        Resource resource = resourceLoader.getResource(templatePath);

        if (!resource.exists()) {
            log.error("Template resource not found: {}", templatePath);
            throw new RenderingException("Template not found: " + templatePath);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            // 编译模板
            XWPFTemplate template = XWPFTemplate.compile(inputStream);
            // 渲染数据
            template.render(renderData);
            // 将渲染结果写入指定的输出流并关闭 poi-tl 资源
            // 注意：这里不关闭传入的 outputStream
            template.writeAndClose(outputStream);
            log.debug("Report rendered successfully to output stream");

        } catch (IOException e) {
            log.error("IO error during template loading or rendering for path: {}", templatePath, e);
            throw new RenderingException("IO error during rendering for template: " + templatePath, e);
        } catch (Exception e) {
            log.error("Error rendering report with poi-tl for path: {}", templatePath, e);
            throw new RenderingException("Rendering failed for template: " + templatePath, e);
        }
    }

}
