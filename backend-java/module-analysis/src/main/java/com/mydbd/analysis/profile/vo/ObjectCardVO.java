package com.mydbd.analysis.profile.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** F23 对象画像响应（MOD-ANA-002 §4.2） */
@Data
public class ObjectCardVO {
    private String type;
    /** vehicle=车牌；driver/dept=id 字符串 */
    private String id;
    private String name;
    /** 副标题：车辆=当前主班司机，司机=当前绑定车牌（首个），企业=车辆数说明 */
    private String sub;
    private long eventTotal;
    private long highCnt;

    /** F22 driver_score 是否就绪（表缺失/无数据降级） */
    private boolean scoreReady;
    private ScoreCurve scoreCurve;
    private LatestScore latestScore;

    private List<CompositionItem> composition;
    private Funnel funnel;
    /** 工单 Top5（列名见 ProfileOrderMapper.topOrders） */
    private List<Map<String, Object>> topOrders;

    @Data
    public static class ScoreCurve {
        private List<String> dates;
        private List<BigDecimal> scores;
        private List<String> levels;
    }

    @Data
    public static class LatestScore {
        private BigDecimal score;
        private String level;

        public LatestScore() {
        }

        public LatestScore(BigDecimal score, String level) {
            this.score = score;
            this.level = level;
        }
    }

    @Data
    public static class CompositionItem {
        private String code;
        private String name;
        private long cnt;
        private long high;
        private long mid;
        private long low;
    }

    @Data
    public static class Funnel {
        private long eventTotal;
        private long orderTotal;
        private long interventionTotal;
        private long closedTotal;
        /** ②=0 时 null，前端显示"—" */
        private BigDecimal interventionRate;
        private BigDecimal closeRate;
    }
}
