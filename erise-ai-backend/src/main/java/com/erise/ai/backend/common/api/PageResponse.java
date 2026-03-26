package com.erise.ai.backend.common.api;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long pageNum,
        long pageSize,
        long total,
        long totalPages
) {
    public static <T> PageResponse<T> of(List<T> records, long pageNum, long pageSize, long total) {
        long totalPages = pageSize <= 0 ? 1 : (long) Math.ceil((double) total / pageSize);
        return new PageResponse<>(records, pageNum, pageSize, total, totalPages);
    }
}
