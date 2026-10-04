package com.mydbd.monitor.geocode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.Result;
import com.mydbd.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端扩展能力：地理编码（前端契约见 frontend/src/api/terminal.ts）
 *
 * <p>服务端代理调用高德 Web 服务，避免 Key 暴露在前端。
 * 坐标系约定：平台点位坐标与监控地图底图（高德瓦片）同为 GCJ-02，直接透传；
 * 若未来接入 WGS-84 原始 GPS 数据，应在数据接入层统一转换。
 *
 * <ul>
 *   <li>GET /api/terminal/geocode/reverse?lng=&lat=  → { address }</li>
 *   <li>GET /api/terminal/geocode/search?keyword=&city= → [{ name, address, lng, lat }]</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/terminal/geocode")
public class GeocodeController {

    private static final Logger log = LoggerFactory.getLogger(GeocodeController.class);
    private static final String AMAP_BASE = "https://restapi.amap.com";

    private final AmapProperties amap;
    private final RestTemplate http;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeocodeController(AmapProperties amap) {
        this.amap = amap;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.http = new RestTemplate(factory);
        // 高德返回 UTF-8，必须替换默认 ISO-8859-1 的 StringHttpMessageConverter
        List<org.springframework.http.converter.HttpMessageConverter<?>> converters = new ArrayList<>(this.http.getMessageConverters());
        converters.removeIf(c -> c instanceof StringHttpMessageConverter);
        converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.http.setMessageConverters(converters);
    }

    /** 逆地理编码：经纬度 → 文字地址 */
    @GetMapping("/reverse")
    public Result<Map<String, String>> reverse(@RequestParam double lng, @RequestParam double lat) {
        ensureConfigured();
        if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
            throw new BizException(ErrorCode.BAD_REQUEST, "坐标超出合法范围");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(AMAP_BASE + "/v3/geocode/regeo")
                .queryParam("output", "json")
                .queryParam("location", lng + "," + lat)
                .queryParam("key", amap.getKey())
                .build(true).toUri();
        JsonNode root = call(uri);
        JsonNode addressNode = root.path("regeocode").path("formatted_address");
        // 无结果时高德可能返回数组而非字符串
        String address = addressNode.isTextual() ? addressNode.asText() : "";
        return Result.ok(Map.of("address", address));
    }

    /** 地点检索：关键字 → 候选坐标点（监控页地图地址搜索框） */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestParam String keyword,
                                                    @RequestParam(required = false) String city) {
        ensureConfigured();
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return Result.ok(List.of());
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(AMAP_BASE + "/v3/place/text")
                .queryParam("output", "json")
                .queryParam("keywords", kw)
                .queryParam("citylimit", "true")
                .queryParam("key", amap.getKey());
        if (city != null && !city.isBlank()) {
            builder.queryParam("city", city.trim());
        }
        JsonNode root = call(builder.build().encode().toUri());
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode poi : root.path("pois")) {
            String location = poi.path("location").asText("");
            int comma = location.indexOf(',');
            if (comma <= 0) {
                continue;
            }
            try {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", poi.path("name").asText(""));
                JsonNode addr = poi.path("address");
                item.put("address", addr.isTextual() ? addr.asText() : "");
                item.put("lng", Double.parseDouble(location.substring(0, comma)));
                item.put("lat", Double.parseDouble(location.substring(comma + 1)));
                list.add(item);
            } catch (NumberFormatException ignored) {
                // 跳过坐标非法的条目
            }
        }
        return Result.ok(list);
    }

    private void ensureConfigured() {
        if (!amap.isConfigured()) {
            throw new BizException(50301, "高德地图 Key 未配置（环境变量 AMAP_KEY），地理编码服务暂不可用");
        }
    }

    /** 调用高德并做统一错误归一化；status!=1 视为业务失败 */
    private JsonNode call(URI uri) {
        String body;
        try {
            body = http.getForObject(uri, String.class);
        } catch (RestClientException e) {
            log.warn("高德服务调用失败: {}", e.getMessage());
            throw new BizException(50301, "高德地图服务暂时不可用");
        }
        try {
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            if (!"1".equals(root.path("status").asText())) {
                String info = root.path("info").asText("unknown");
                log.warn("高德服务返回失败: info={}, infocode={}", info, root.path("infocode").asText());
                throw new BizException(50000, "高德地图服务返回失败：" + info);
            }
            return root;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("高德响应解析失败: {}", e.getMessage());
            throw new BizException(50000, "高德地图服务响应异常");
        }
    }
}
