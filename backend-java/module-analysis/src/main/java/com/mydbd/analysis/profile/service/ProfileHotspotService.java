package com.mydbd.analysis.profile.service;

import com.mydbd.analysis.profile.constant.ProfileConstants;
import com.mydbd.analysis.profile.dto.HotspotRow;
import com.mydbd.analysis.profile.mapper.ProfileEventMapper;
import com.mydbd.analysis.profile.vo.HotspotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * F23 黑点路段（MOD-ANA-002 §3.4）：3857 网格吸附聚合，近似口径。
 */
@Service
@RequiredArgsConstructor
public class ProfileHotspotService {

    private final ProfileEventMapper eventMapper;
    private final ProfileSupport support;

    public HotspotVO hotspots(LocalDateTime start, LocalDateTime end, int radiusM, int limit) {
        if (!ProfileConstants.RADIUS_WHITELIST.contains(radiusM)) {
            throw new com.mydbd.common.exception.BizException(
                    ProfileConstants.ERR_DIM, "radius_m 仅支持 100/200/500");
        }
        HotspotVO vo = new HotspotVO();
        vo.setRadiusM(radiusM);
        vo.setApprox(true);
        vo.setNote(ProfileConstants.HOTSPOT_NOTE);
        vo.setItems(List.of());

        List<Long> scope = support.scopeOrNull();
        if (scope != null && scope.isEmpty()) {
            return vo;
        }
        List<HotspotRow> rows = eventMapper.hotspots(start, end, radiusM, limit, scope);
        List<HotspotVO.Item> items = new ArrayList<>();
        int rank = 1;
        for (HotspotRow r : rows) {
            HotspotVO.Item it = new HotspotVO.Item();
            it.setRank(rank++);
            it.setLng(r.getLng());
            it.setLat(r.getLat());
            it.setEventCnt(r.getEventCnt());
            it.setWeightedScore(r.getWeightedScore());
            it.setPlateCnt(r.getPlateCnt());
            it.setTopPlate(r.getTopPlate());
            it.setTopCode(r.getTopCode());
            items.add(it);
        }
        vo.setItems(items);
        return vo;
    }
}
