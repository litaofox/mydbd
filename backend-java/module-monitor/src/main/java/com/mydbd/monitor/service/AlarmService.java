package com.mydbd.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydbd.common.api.ErrorCode;
import com.mydbd.common.api.PageData;
import com.mydbd.common.exception.BizException;
import com.mydbd.common.security.UserContext;
import com.mydbd.common.security.UserInfo;
import com.mydbd.monitor.dto.AlarmQuery;
import com.mydbd.monitor.dto.ResolveRequest;
import com.mydbd.monitor.entity.WarnInfo;
import com.mydbd.monitor.mapper.RiskEventMapper;
import com.mydbd.monitor.mapper.WarnInfoMapper;
import com.mydbd.monitor.vo.AlarmVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F17 终端报警中心服务（MOD-MON-004 §3/§4）。
 * 状态机 0→1→2、0→2；并发冲突 45001；handler = 当前用户 realName（空回退 username，截 30）。
 */
@Service
@RequiredArgsConstructor
public class AlarmService {

    /** 本模块专用冲突码（RES-DBD-001 §3.2），不复用通用 40901 */
    public static final int ERR_STATE_CONFLICT = 45001;

    private static final Set<String> RESOLVE_CODES = Set.of("00", "01", "02");
    private static final int MSG_MAX = 200;
    private static final int HANDLER_MAX = 30;

    private final WarnInfoMapper warnInfoMapper;
    private final RiskEventMapper riskEventMapper;

    /** §4.1 分页：size 钳制 1~100，时间非法 40001 */
    public PageData<AlarmVO> page(AlarmQuery q) {
        long size = Math.min(Math.max(q.getSize(), 1), 100);
        long page = Math.max(q.getPage(), 1);
        Page<AlarmVO> result = warnInfoMapper.pageAlarms(
                new Page<>(page, size),
                StringUtils.hasText(q.getPlateNo()) ? q.getPlateNo().trim() : null,
                q.getTypeId(),
                q.getHandleStatus(),
                parseTime(q.getBeginTime(), "beginTime"),
                parseTime(q.getEndTime(), "endTime"));
        return new PageData<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    /** §4.2 待处理滚动：limit 钳制 1~50（越界不报错） */
    public List<AlarmVO> latest(Integer limit) {
        int n = limit == null ? 10 : Math.min(Math.max(limit, 1), 50);
        return warnInfoMapper.selectLatestPending(n);
    }

    /** §4.3 统计：缺省=今日 00:00 至当前 */
    public Map<String, Object> stats(String start, String end) {
        LocalDateTime begin = parseTime(start, "start");
        LocalDateTime finish = parseTime(end, "end");
        if (begin == null) {
            begin = LocalDate.now().atStartOfDay();
        }
        if (finish == null) {
            finish = LocalDateTime.now();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", warnInfoMapper.countInRange(begin, finish));
        data.put("byType", warnInfoMapper.countByType(begin, finish));
        data.put("byStatus", warnInfoMapper.countByStatus(begin, finish));
        return data;
    }

    /** §4.5 详情：不存在 40401；附带 ±30min 同车牌关联风险前 5 条 */
    public AlarmVO detail(Long id) {
        AlarmVO vo = warnInfoMapper.selectDetail(id);
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报警不存在");
        }
        if (StringUtils.hasText(vo.getPlateNo()) && vo.getStartWarnTime() != null) {
            vo.setRelatedRisks(riskEventMapper.relatedRisks(vo.getPlateNo(), vo.getStartWarnTime()));
        } else {
            vo.setRelatedRisks(List.of());
        }
        return vo;
    }

    /** §4.5 类型下拉 */
    public List<Map<String, Object>> types() {
        return warnInfoMapper.selectAlarmTypes();
    }

    /** §4.4 确认 0→1 */
    @Transactional
    public AlarmVO confirm(Long id) {
        requireExists(id);
        String name = currentHandler();
        if (warnInfoMapper.markConfirmed(id, name) == 0) {
            throw new BizException(ERR_STATE_CONFLICT, "报警状态已变更，请刷新后重试");
        }
        return warnInfoMapper.selectDetail(id);
    }

    /** §4.4 解除 0/1→2：resultCode 必填合法，resultMsg ≤200 */
    @Transactional
    public AlarmVO resolve(Long id, ResolveRequest req) {
        String code = req == null ? null : req.resultCode();
        String msg = req == null ? null : req.resultMsg();
        if (code == null || !RESOLVE_CODES.contains(code)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "resultCode 仅支持 00/01/02");
        }
        if (msg != null && msg.length() > MSG_MAX) {
            throw new BizException(ErrorCode.BAD_REQUEST, "resultMsg 不得超过 200 字");
        }
        requireExists(id);
        String name = currentHandler();
        if (warnInfoMapper.markResolved(id, name, code, StringUtils.hasText(msg) ? msg.trim() : null) == 0) {
            throw new BizException(ERR_STATE_CONFLICT, "报警状态已变更，请刷新后重试");
        }
        return warnInfoMapper.selectDetail(id);
    }

    private void requireExists(Long id) {
        WarnInfo raw = warnInfoMapper.selectById(id);
        if (raw == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报警不存在");
        }
    }

    /** handler = realName（空回退 username），varchar(30) 截断防御 */
    private String currentHandler() {
        UserInfo user = UserContext.get();
        String name = null;
        if (user != null) {
            name = StringUtils.hasText(user.realName()) ? user.realName() : user.username();
        }
        if (!StringUtils.hasText(name)) {
            return "system";
        }
        return name.length() > HANDLER_MAX ? name.substring(0, HANDLER_MAX) : name;
    }

    private LocalDateTime parseTime(String v, String field) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        try {
            return LocalDateTime.parse(v.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 需为 yyyy-MM-dd'T'HH:mm:ss: " + v);
        }
    }
}
