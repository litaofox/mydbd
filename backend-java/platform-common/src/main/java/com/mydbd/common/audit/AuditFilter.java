package com.mydbd.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mydbd.common.security.UserInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 审计采集过滤器。
 * <p>注册 order=0，包在 JwtAuthFilter 外层：即使鉴权过滤器短路（无 Token/Token 失效），
 * 本过滤器仍可在链返回后记录匿名失败访问。
 * <p>任何审计自身异常只输出 error 日志，绝不影响业务响应。
 */
@Slf4j
public class AuditFilter extends OncePerRequestFilter {

    /** JwtAuthFilter 解析成功后把 UserInfo 放入该 request 属性 */
    public static final String ATTR_USER = "mydbd.audit.user";
    /** AuditAspect 解析出的补充语义 */
    public static final String ATTR_SEMANTIC = "mydbd.audit.semantic";

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final int BODY_MAX = 2000;
    private static final int QUERY_MAX = 512;
    private static final int UA_MAX = 256;
    private static final int ERROR_MAX = 500;

    private static final Set<String> RECORD_METHODS = Set.of("GET", "POST", "PUT", "DELETE");

    private static final Map<String, String> ACTION_NAMES = Map.of(
            AuditAction.LOGIN, "登录成功",
            AuditAction.LOGIN_FAIL, "登录失败",
            AuditAction.CREATE, "新增",
            AuditAction.UPDATE, "修改",
            AuditAction.DELETE, "删除",
            AuditAction.EXPORT, "数据导出",
            AuditAction.VIDEO_VIEW, "视频调阅",
            AuditAction.HANDLE, "业务处置",
            AuditAction.QUERY, "敏感查询");

    /** 路径资源段 → 对象类型兜底映射 */
    private static final Map<String, String> OBJECT_TYPES = Map.ofEntries(
            Map.entry("vehicles", "VEHICLE"),
            Map.entry("terminals", "TERMINAL"),
            Map.entry("drivers", "DRIVER"),
            Map.entry("orgs", "DEPT"),
            Map.entry("depts", "DEPT"),
            Map.entry("bindings", "BINDING"),
            Map.entry("users", "USER"),
            Map.entry("roles", "ROLE"),
            Map.entry("rules", "RULE"),
            Map.entry("events", "RISK_EVENT"),
            Map.entry("orders", "WORK_ORDER"));

    /** 键名命中即整体打码（小写匹配；另对含 token/secret 的键统一打码） */
    private static final Set<String> MASKED_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "token", "secret",
            "servicetoken", "jwt", "idcard", "totpcode", "mfacode", "verificationcode");

    private final ApplicationEventPublisher publisher;
    private final AuditProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuditFilter(ApplicationEventPublisher publisher, AuditProperties properties,
                       ObjectMapper objectMapper) {
        this.publisher = publisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase();
        if (!RECORD_METHODS.contains(method)) {
            return true;
        }
        String uri = request.getRequestURI();
        for (String pattern : properties.getExcludePaths()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper resp = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        try {
            chain.doFilter(req, resp);
        } finally {
            try {
                record(req, resp, traceId, (int) (System.currentTimeMillis() - start));
            } catch (Exception ex) {
                log.error("审计记录组装失败 uri={}", request.getRequestURI(), ex);
            } finally {
                resp.copyBodyToResponse();
            }
        }
    }

    private void record(ContentCachingRequestWrapper req, ContentCachingResponseWrapper resp,
                        String traceId, int costMs) {
        String method = req.getMethod().toUpperCase();
        String uri = req.getRequestURI();
        boolean login = LOGIN_PATH.equals(uri);
        HandlerMethod handler = resolveHandlerMethod(req);
        AuditLog ann = handler == null ? null : handler.getMethodAnnotation(AuditLog.class);

        boolean shouldRecord;
        if (login) {
            shouldRecord = true;
        } else if ("GET".equals(method)) {
            shouldRecord = ann != null;
        } else {
            shouldRecord = true;
        }
        if (!shouldRecord) {
            return;
        }

        JsonNode bodyTree = readBodyTree(req);
        JsonNode respTree = readTree(resp.getContentAsByteArray());

        int httpStatus = resp.getStatus();
        Integer bizCode = respTree != null && respTree.has("code") ? respTree.get("code").asInt() : null;
        String message = respTree != null ? respTree.path("message").asText(null) : null;
        boolean success = httpStatus < 400 && (bizCode == null || bizCode == 0);

        // 模块/动作/对象语义
        String module;
        String action;
        String actionName;
        String objectType;
        if (login) {
            module = "AUTH";
            action = success ? AuditAction.LOGIN : AuditAction.LOGIN_FAIL;
            actionName = ACTION_NAMES.get(action);
            objectType = null;
        } else if (ann != null) {
            module = blankToDefault(ann.module(), guessModule(uri));
            action = blankToDefault(ann.action(), defaultAction(method));
            actionName = blankToDefault(ann.actionName(), ACTION_NAMES.get(action));
            objectType = blankToDefault(ann.objectType(), guessObjectType(uri));
        } else {
            module = guessModule(uri);
            action = defaultAction(method);
            actionName = ACTION_NAMES.get(action);
            objectType = guessObjectType(uri);
        }

        String objectId = resolveObjectId(req, ann, uri, method, respTree);
        String userName = resolveUserName(req, login, bodyTree);
        String maskedBody = bodyTree == null ? null : truncate(writeMasked(bodyTree), BODY_MAX);

        AuditEvent event = new AuditEvent(
                traceId,
                userName,
                module,
                action,
                actionName,
                objectType,
                objectId,
                method,
                uri,
                truncate(req.getQueryString(), QUERY_MAX),
                maskedBody,
                success ? 1 : 0,
                bizCode,
                success ? null : truncate(message, ERROR_MAX),
                costMs,
                resolveClientIp(req),
                truncate(req.getHeader("User-Agent"), UA_MAX),
                LocalDateTime.now());
        publisher.publishEvent(event);
    }

    private String resolveObjectId(ContentCachingRequestWrapper req, AuditLog ann,
                                   String uri, String method, JsonNode respTree) {
        // 1) AOP SpEL 解析结果
        Object semantic = req.getAttribute(ATTR_SEMANTIC);
        if (semantic instanceof AuditSemantic s && s.objectId() != null && !s.objectId().isBlank()) {
            return s.objectId();
        }
        // 2) 新增成功：响应 Result.data 中的 id
        if ("POST".equals(method) && respTree != null) {
            JsonNode data = respTree.get("data");
            String fromData = extractId(data);
            if (fromData != null) {
                return fromData;
            }
        }
        // 3) 路径末段为数字（雪花ID）
        String[] segments = uri.split("/");
        String last = segments[segments.length - 1];
        if (last.matches("\\d{1,19}")) {
            return last;
        }
        return null;
    }

    private String extractId(JsonNode data) {
        if (data == null || data.isNull()) {
            return null;
        }
        if (data.isNumber() || data.isTextual()) {
            return data.asText();
        }
        if (data.has("id") && !data.get("id").isNull()) {
            return data.get("id").asText();
        }
        return null;
    }

    private String resolveUserName(ContentCachingRequestWrapper req, boolean login, JsonNode bodyTree) {
        if (login) {
            // 登录接口不经 JwtAuthFilter，用户名从请求体取（失败也要记）
            if (bodyTree != null) {
                JsonNode username = bodyTree.get("username");
                if (username != null && !username.isNull()) {
                    return username.asText();
                }
            }
            return null;
        }
        Object attr = req.getAttribute(ATTR_USER);
        if (attr instanceof UserInfo user) {
            return user.username();
        }
        return null;
    }

    private HandlerMethod resolveHandlerMethod(HttpServletRequest req) {
        Object attr = req.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        return attr instanceof HandlerMethod hm ? hm : null;
    }

    private String guessModule(String uri) {
        String[] segments = uri.split("/");
        return segments.length >= 3 ? segments[2].toUpperCase() : "UNKNOWN";
    }

    private String guessObjectType(String uri) {
        String[] segments = uri.split("/");
        if (segments.length < 4) {
            return null;
        }
        // 末段为数字ID时资源是倒数第2段（PUT/DELETE），否则资源就是末段（POST 集合）
        String last = segments[segments.length - 1];
        String resource = last.matches("\\d{1,19}")
                ? segments[segments.length - 2]
                : last;
        return OBJECT_TYPES.get(resource);
    }

    private String defaultAction(String method) {
        return switch (method) {
            case "POST" -> AuditAction.CREATE;
            case "PUT" -> AuditAction.UPDATE;
            case "DELETE" -> AuditAction.DELETE;
            default -> AuditAction.QUERY;
        };
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return req.getRemoteAddr();
    }

    private JsonNode readBodyTree(ContentCachingRequestWrapper req) {
        byte[] bytes = req.getContentAsByteArray();
        if (bytes.length == 0) {
            return null;
        }
        return readTree(bytes);
    }

    private JsonNode readTree(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return objectMapper.readTree(bytes);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 脱敏后序列化：password/token/secret/idcard 类键打码；手机号类键前3后4。
     */
    private String writeMasked(JsonNode tree) {
        try {
            maskNode(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (Exception ex) {
            return null;
        }
    }

    private void maskNode(JsonNode node) {
        if (node == null || !node.isContainerNode()) {
            return;
        }
        if (node.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode obj =
                    (com.fasterxml.jackson.databind.node.ObjectNode) node;
            // 遍历中只替换已有键的值（不增删结构），ConcurrentModificationException 不会发生
            obj.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase();
                JsonNode value = entry.getValue();
                if (value.isValueNode()
                        && (MASKED_KEYS.contains(key) || key.contains("token") || key.contains("secret"))) {
                    obj.set(entry.getKey(), TextNode.valueOf("***"));
                } else if ((key.contains("phone") || key.contains("mobile")) && value.isTextual()) {
                    obj.set(entry.getKey(), TextNode.valueOf(maskPhone(value.asText())));
                } else {
                    maskNode(value);
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::maskNode);
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
