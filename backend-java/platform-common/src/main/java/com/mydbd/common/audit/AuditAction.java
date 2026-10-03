package com.mydbd.common.audit;

/**
 * 审计动作字典（MOD-AUDIT-001 §5.1）
 */
public final class AuditAction {

    /** 登录成功 */
    public static final String LOGIN = "LOGIN";
    /** 登录失败 */
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    /** 新增 */
    public static final String CREATE = "CREATE";
    /** 修改 */
    public static final String UPDATE = "UPDATE";
    /** 删除（业务软删除亦记此动作） */
    public static final String DELETE = "DELETE";
    /** 数据导出 */
    public static final String EXPORT = "EXPORT";
    /** 视频调阅 */
    public static final String VIDEO_VIEW = "VIDEO_VIEW";
    /** 业务处置（报警确认/派单/坐席干预） */
    public static final String HANDLE = "HANDLE";
    /** 敏感查询 */
    public static final String QUERY = "QUERY";

    private AuditAction() {
    }
}
