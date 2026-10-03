package com.mydbd.analysis.profile.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** F23 对比排行响应（§4.4）。id：dim=driver 为司机 id 字符串（雪花安全），dim=vehicle 为车牌。 */
@Data
public class RankingVO {
    private String dim;
    private List<Item> items;

    @Data
    public static class Item {
        private int rank;
        private String id;
        private String name;
        private String sub;
        private long eventCnt;
        private long highCnt;
        private long weightedScore;
        /** F22 未就绪时 null */
        private BigDecimal score;
        private String level;
    }
}
