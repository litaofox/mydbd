package com.mydbd.monitor.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * vps 网关接入状态只读查询（GATEWAY-PLAN-001）：直查 traj.gateway_unknown_terminal 隔离区。
 */
public interface GatewayStatusMapper {

    /** 未登记终端隔离区（最近活跃优先，封顶 500 条） */
    @Select("""
            SELECT id, phone_number AS "phoneNumber", truck_id AS "truckId",
                   plate_no AS "plateNo", msg_count AS "msgCount",
                   first_seen AS "firstSeen", last_seen AS "lastSeen"
              FROM traj.gateway_unknown_terminal
             ORDER BY last_seen DESC
             LIMIT 500
            """)
    List<Map<String, Object>> selectUnknownTerminals();
}
