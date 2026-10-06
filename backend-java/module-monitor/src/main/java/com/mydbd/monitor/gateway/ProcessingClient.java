package com.mydbd.monitor.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * processing-service 内部接口客户端（GATEWAY-PLAN-001）。
 * 所有调用携带 X-Service-Token 头（mydbd.service-token，环境变量 SERVICE_TOKEN 注入）。
 */
@Component
public class ProcessingClient {

    private static final Logger log = LoggerFactory.getLogger(ProcessingClient.class);

    /** 网关服务不可用时沿用 GeocodeController 的 50301 口径 */
    private static final int ERR_UPSTREAM_UNAVAILABLE = 50301;

    private final GatewayProperties properties;
    private final String serviceToken;
    private final RestTemplate http;

    public ProcessingClient(GatewayProperties properties,
                            @Value("${mydbd.service-token:}") String serviceToken) {
        this.properties = properties;
        this.serviceToken = serviceToken;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(30000);
        this.http = new RestTemplate(factory);
    }

    /** GET JSON 接口（如 /api/gateway/status），原样透传响应体 */
    public JsonNode getJson(String path) {
        try {
            ResponseEntity<JsonNode> resp = http.exchange(properties.getBaseUrl() + path,
                    HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class);
            return resp.getBody();
        } catch (RestClientException e) {
            log.warn("processing 服务调用失败: path={}, {}", path, e.getMessage());
            throw new BizException(ERR_UPSTREAM_UNAVAILABLE, "网关接入服务暂时不可用");
        }
    }

    /**
     * GET 文件流接口（如 /api/gateway/media/{id}）：字节流 + Content-Type 透传，
     * 经 ResponseExtractor 直接复制流，不整报文读入内存。
     * 上游 404 归一为 40401；其余失败归一为 50301。
     */
    public void stream(String path, HttpServletResponse response) {
        try {
            http.execute(properties.getBaseUrl() + path, HttpMethod.GET,
                    req -> req.getHeaders().set("X-Service-Token", serviceToken),
                    resp -> {
                        MediaType contentType = resp.getHeaders().getContentType();
                        if (contentType != null) {
                            response.setContentType(contentType.toString());
                        }
                        long contentLength = resp.getHeaders().getContentLength();
                        if (contentLength >= 0) {
                            response.setContentLengthLong(contentLength);
                        }
                        StreamUtils.copy(resp.getBody(), response.getOutputStream());
                        response.flushBuffer();
                        return null;
                    });
        } catch (HttpClientErrorException.NotFound e) {
            throw new BizException(ErrorCode.NOT_FOUND, "附件不存在或尚未转存");
        } catch (RestClientException e) {
            log.warn("processing 服务调用失败: path={}, {}", path, e.getMessage());
            throw new BizException(ERR_UPSTREAM_UNAVAILABLE, "网关接入服务暂时不可用");
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", serviceToken);
        return headers;
    }
}
