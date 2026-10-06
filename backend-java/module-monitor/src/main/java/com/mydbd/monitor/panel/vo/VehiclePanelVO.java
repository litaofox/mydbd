package com.mydbd.monitor.panel.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F16 车辆详情聚合面板出参（MOD-MON-003 §3.2）。
 * 所有 Long id 字段 ToStringSerializer，前端类型为 string。
 */
@Data
public class VehiclePanelVO {

    private VehicleBrief vehicle;

    /** 当前有效绑定终端；无绑定 = null */
    private TerminalBrief terminal;

    /** 主班(1)+副班(2)司机，按 driver_type、bind_time 排序 */
    private List<DriverBrief> drivers;

    /** 最新定位点；无终端绑定或无历史点 = null */
    private LatestPoint latestPoint;

    /** 当日轨迹（抽稀 ≤500 点） */
    private TodayTrack todayTrack;

    /** 最近 5 条终端报警 */
    private List<PanelAlarm> latestAlarms;

    private long todayAlarmCount;

    private long todayRiskCount;

    @Data
    public static class VehicleBrief {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long deptId;
        private String deptName;
        private String vehicleNo;
        private String vehiclePlateColor;
        private String vehicleType;
        private String vehicleBrand;
        private String vin;
        private Integer operationType;
        private String ownerName;
        private String ownerPhone;
        private String roadLicenseNo;
        private String remark;
    }

    @Data
    public static class TerminalBrief {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String identityCode;
        private String tlModel;
        private String simAccount;
        private String protocolType;
        private String equipmentType;
        private Integer videoChannel;
        private Integer status;
        /** 网关在案在线状态：0 离线 1 在线（GATEWAY-PLAN-001；NULL=未知，回退点位窗口口径） */
        private Integer onlineStatus;
        private LocalDateTime bindTime;
    }

    @Data
    public static class DriverBrief {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String driverName;
        private Integer sex;
        private String contactPhone;
        private String licenceCategory;
        private Integer driverType;
        private Integer status;
        private LocalDateTime bindTime;
    }

    @Data
    public static class LatestPoint {
        private String identityCode;
        private String plateNo;
        private Double lng;
        private Double lat;
        private Integer speed;
        private Integer direction;
        private LocalDateTime gpsTime;
        private Integer alarmFlag;
        /** 终端 online_status=1 优先；状态未知时回退 gpsTime 距今 ≤5 分钟（与 F14/大屏口径一致） */
        private Boolean online;
    }

    @Data
    public static class TodayTrack {
        private String date;
        /** 原始点数 */
        private long totalPoints;
        /** 是否发生抽稀 */
        private boolean sampled;
        private int maxPoints = 500;
        private List<TrackPoint> points;
    }

    @Data
    public static class TrackPoint {
        private LocalDateTime gpsTime;
        private Double lng;
        private Double lat;
        private Integer speed;
    }

    @Data
    public static class PanelAlarm {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private Integer typeId;
        private String typeName;
        private LocalDateTime startWarnTime;
        /** 0 待处理 / 1 已确认 / 2 已解除（COALESCE 归一） */
        private Integer handleStatus;
        private String startLng;
        private String startLat;
    }
}
