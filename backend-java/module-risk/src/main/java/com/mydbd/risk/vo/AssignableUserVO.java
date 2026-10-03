package com.mydbd.risk.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** F20 可分派用户（id 雪花，序列化为字符串避免 JS 精度丢失） */
@Data
public class AssignableUserVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String username;
}
