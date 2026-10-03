package com.mydbd.risk.vo;

import com.mydbd.risk.entity.RiskEventRef;
import com.mydbd.risk.entity.RiskOrderLog;
import com.mydbd.risk.entity.RiskWorkOrder;
import lombok.Data;

import java.util.List;

/** F20 工单详情：工单 + 事件全量 + 流转时间线 */
@Data
public class OrderDetailVO {

    private RiskWorkOrder order;

    private RiskEventRef event;

    private List<RiskOrderLog> logs;
}
