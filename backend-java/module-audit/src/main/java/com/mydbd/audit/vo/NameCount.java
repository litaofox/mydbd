package com.mydbd.audit.vo;

import lombok.Data;

/**
 * 统计用名值对
 */
@Data
public class NameCount {

    private String name;

    private Long count;
}
