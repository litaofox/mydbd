package com.mydbd.iam.mapper;

import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据范围计算用：组织架构只读视图（避免与 module-mdm 的 DeptMapper 产生 Bean/实体耦合）
 */
public interface DataScopeDeptMapper {

    /**
     * 全量有效部门（组织规模小，一次加载后在 Java 内完成祖先/子树计算）
     */
    @Select("SELECT id, parent_id AS \"parentId\", dept_type AS \"deptType\" "
            + "FROM traj.traj_dept WHERE valid_mark = 1")
    List<DeptNode> selectAllNodes();

    /**
     * 部门节点（数据范围计算）
     */
    record DeptNode(Long id, Long parentId, Integer deptType) {
    }
}
