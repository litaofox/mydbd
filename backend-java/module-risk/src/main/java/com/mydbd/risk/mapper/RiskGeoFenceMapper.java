package com.mydbd.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydbd.risk.entity.RiskGeoFence;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * F18 电子围栏 Mapper：标量走 BaseMapper，polygon_geom 走 PostGIS 函数。
 */
public interface RiskGeoFenceMapper extends BaseMapper<RiskGeoFence> {

    /** 返回 GeoJSON Polygon 字符串（无图形时为 null） */
    @Select("SELECT ST_AsGeoJSON(polygon_geom) FROM mon.risk_geo_fence WHERE id = #{id}")
    String selectPolygonGeoJson(@Param("id") Long id);

    /** 用 GeoJSON 覆盖多边形（ST_SetSRID 补 4326） */
    @Update("UPDATE mon.risk_geo_fence "
            + "SET polygon_geom = ST_SetSRID(ST_GeomFromGeoJSON(#{geojson}), 4326), "
            + "update_date = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updatePolygonGeom(@Param("id") Long id, @Param("geojson") String geojson);

    /** 圆形围栏清空多边形列 */
    @Update("UPDATE mon.risk_geo_fence SET polygon_geom = NULL, "
            + "update_date = CURRENT_TIMESTAMP WHERE id = #{id}")
    int clearPolygonGeom(@Param("id") Long id);

    /** 围栏删除后清理车辆状态（再启用时按首见处理） */
    @Delete("DELETE FROM mon.risk_fence_state WHERE fence_id = #{id}")
    int deleteFenceState(@Param("id") Long id);
}
