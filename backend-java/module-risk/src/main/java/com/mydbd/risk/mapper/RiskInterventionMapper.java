package com.mydbd.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.risk.entity.RiskIntervention;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface RiskInterventionMapper extends BaseMapper<RiskIntervention> {

    /** 统计指定时间范围内创建的工单中，存在干预记录的工单数（distinct order_id） */
    @Select("SELECT COUNT(DISTINCT order_id) FROM mon.risk_intervention " +
            "WHERE order_id IN (SELECT id FROM mon.risk_work_order WHERE valid_mark=1 " +
            "AND create_date >= #{start} AND create_date < #{end})")
    long countIntervenedOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
