package com.mydbd.analysis.profile.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** F23 黑点路段响应（§4.3） */
@Data
public class HotspotVO {
    private int radiusM;
    private boolean approx;
    private String note;
    private List<Item> items;

    @Data
    public static class Item {
        private int rank;
        private BigDecimal lng;
        private BigDecimal lat;
        private long eventCnt;
        private long weightedScore;
        private long plateCnt;
        private String topPlate;
        private String topCode;
    }
}
