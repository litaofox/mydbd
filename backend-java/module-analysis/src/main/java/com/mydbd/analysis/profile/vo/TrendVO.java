package com.mydbd.analysis.profile.vo;

import lombok.Data;

import java.util.List;

/** F23 趋势响应（MOD-ANA-002 §4.1） */
@Data
public class TrendVO {
    private String dim;
    private String id;
    private String granularity;
    /** 桶标签序列（day: yyyy-MM-dd / week: yyyy-MM-dd(周一) / month: yyyy-MM），空桶补零后连续 */
    private List<String> buckets;
    private List<Long> total;
    private LevelSeries byLevel;
    private List<Series> topCodes;

    @Data
    public static class LevelSeries {
        private List<Long> high;
        private List<Long> mid;
        private List<Long> low;
    }

    @Data
    public static class Series {
        private String code;
        private String name;
        private List<Long> series;

        public Series() {
        }

        public Series(String code, String name, List<Long> series) {
            this.code = code;
            this.name = name;
            this.series = series;
        }
    }
}
