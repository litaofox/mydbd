package com.mydbd.common.api;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页数据结构
 */
@Data
public class PageData<T> implements Serializable {

    private long total;
    private long page;
    private long size;
    private List<T> records;

    public PageData(long total, long page, long size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records;
    }
}
