package com.mydbd.iam.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点（管理树/导航树共用）
 */
@Data
public class MenuNode {

    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String permCode;
    private String path;
    private String icon;
    private Integer sortNo;
    private Integer visible;
    private List<MenuNode> children = new ArrayList<>();
}
