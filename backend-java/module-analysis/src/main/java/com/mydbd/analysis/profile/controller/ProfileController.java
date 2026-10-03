package com.mydbd.analysis.profile.controller;

import com.mydbd.analysis.profile.constant.ProfileConstants;
import com.mydbd.analysis.profile.service.ProfileCardService;
import com.mydbd.analysis.profile.service.ProfileHotspotService;
import com.mydbd.analysis.profile.service.ProfileTrendService;
import com.mydbd.analysis.profile.vo.HotspotVO;
import com.mydbd.analysis.profile.vo.ObjectCardVO;
import com.mydbd.analysis.profile.vo.RankingVO;
import com.mydbd.analysis.profile.vo.TrendVO;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.Result;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * F23 风险趋势与画像接口（MOD-ANA-002 §4）：全 GET 只读，perm analysis:profile:view。
 * 参数手动解析以精确控制错误码：40001 格式 / 46101 时间范围 / 46102 维度粒度 / 40401 对象不存在。
 */
@RestController
@RequestMapping("/api/analysis/profile")
@RequiredArgsConstructor
@RequiresPerm("analysis:profile:view")
public class ProfileController {

    private final ProfileTrendService trendService;
    private final ProfileCardService cardService;
    private final ProfileHotspotService hotspotService;

    /** 趋势：dim=global|dept|driver|vehicle（默认 global），granularity=day|week|month（默认 day） */
    @GetMapping("/trend")
    public Result<TrendVO> trend(@RequestParam(defaultValue = "global") String dim,
                                 @RequestParam(required = false) String id,
                                 @RequestParam String start,
                                 @RequestParam String end,
                                 @RequestParam(defaultValue = "day") String granularity) {
        if (!ProfileConstants.TREND_DIMS.contains(dim)) {
            throw new BizException(ProfileConstants.ERR_DIM, "dim 仅支持 global/dept/driver/vehicle");
        }
        if (!ProfileConstants.GRANULARITIES.contains(granularity)) {
            throw new BizException(ProfileConstants.ERR_DIM, "granularity 仅支持 day/week/month");
        }
        LocalDateTime[] range = parseRange(start, end);
        return Result.ok(trendService.trend(dim, id, range[0], range[1], granularity));
    }

    /** 对象画像：type=vehicle|driver|dept */
    @GetMapping("/object-card")
    public Result<ObjectCardVO> objectCard(@RequestParam String type,
                                           @RequestParam String id,
                                           @RequestParam String start,
                                           @RequestParam String end) {
        if (!ProfileConstants.CARD_TYPES.contains(type)) {
            throw new BizException(ProfileConstants.ERR_DIM, "type 仅支持 vehicle/driver/dept");
        }
        if (!StringUtils.hasText(id)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "id 必填");
        }
        LocalDateTime[] range = parseRange(start, end);
        return Result.ok(cardService.card(type, id.trim(), range[0], range[1]));
    }

    /** 黑点路段：radius_m ∈ {100,200,500} 默认 200，limit 1~50 默认 20 */
    @GetMapping("/hotspots")
    public Result<HotspotVO> hotspots(@RequestParam String start,
                                      @RequestParam String end,
                                      @RequestParam(name = "radius_m", defaultValue = "200") int radiusM,
                                      @RequestParam(defaultValue = "20") int limit) {
        LocalDateTime[] range = parseRange(start, end);
        return Result.ok(hotspotService.hotspots(range[0], range[1], radiusM, checkLimit(limit, 20)));
    }

    /** 对比排行：dim=driver|vehicle，limit 1~50 默认 10 */
    @GetMapping("/ranking")
    public Result<RankingVO> ranking(@RequestParam String dim,
                                    @RequestParam String start,
                                    @RequestParam String end,
                                    @RequestParam(defaultValue = "10") int limit) {
        if (!ProfileConstants.RANK_DIMS.contains(dim)) {
            throw new BizException(ProfileConstants.ERR_DIM, "ranking dim 仅支持 driver/vehicle");
        }
        LocalDateTime[] range = parseRange(start, end);
        return Result.ok(trendService.ranking(dim, range[0], range[1], checkLimit(limit, 10)));
    }

    /** [start,end) 左闭右开；start<end、跨度≤366 天 → 否则 46101；格式错 → 40001 */
    private LocalDateTime[] parseRange(String start, String end) {
        LocalDateTime s = parseDateTime(start, "start");
        LocalDateTime e = parseDateTime(end, "end");
        if (!s.isBefore(e)) {
            throw new BizException(ProfileConstants.ERR_RANGE, "时间范围非法：start 必须早于 end");
        }
        if (Duration.between(s, e).toDays() > ProfileConstants.MAX_SPAN_DAYS) {
            throw new BizException(ProfileConstants.ERR_RANGE, "时间范围非法：跨度不得超过366天");
        }
        return new LocalDateTime[]{s, e};
    }

    private LocalDateTime parseDateTime(String v, String field) {
        if (!StringUtils.hasText(v)) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 必填");
        }
        try {
            return LocalDateTime.parse(v.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 需为 yyyy-MM-dd'T'HH:mm:ss: " + v);
        }
    }

    private int checkLimit(int limit, int dft) {
        if (limit < 1 || limit > 50) {
            throw new BizException(ErrorCode.BAD_REQUEST, "limit 需在 1~50 之间（默认 " + dft + "）");
        }
        return limit;
    }
}
