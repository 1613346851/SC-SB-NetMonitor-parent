package com.network.monitor.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果DTO
 */
@Data
public class PageResult<T> {

    private List<T> list;

    private long total;

    private int page;

    private int size;

    private int totalPages;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) total / size);
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }
}
