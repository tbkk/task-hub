package com.taskhub.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int total, int page, int pageSize) {
    public PageResponse {
        items = List.copyOf(items);
        if (total < 0 || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
    }
}
