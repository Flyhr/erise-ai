package com.erise.ai.backend.common.api;

import java.util.List;
// 1. 定义一个 泛型分页响应 Record
// Record = 只读的简化实体类，自动生成：构造器/getter/toString 等，不用写get/set
public record PageResponse<T>(
        List<T> records,// 核心：当前页的数据列表（T=销量对象/商品对象）
        long pageNum, // 当前页码（第几页，比如第1页）
        long pageSize,// 每页条数（一页显示10条/20条）
        long total,// 总数据条数（所有销量总和）
        long totalPages // 总页数（根据总条数/每页条数计算）
) {// 2. 静态泛型方法：快速创建分页对象
    // <T>：静态方法必须自己声明泛型
    public static <T> PageResponse<T> of(List<T> records, long pageNum, long pageSize, long total) {
        // 3. 计算总页数：核心逻辑
        // 如果每页条数<=0，总页数=1；否则 总条数/每页条数，向上取整
        long totalPages = pageSize <= 0 ? 1 : (long) Math.ceil((double) total / pageSize);
        // 4. new 一个 PageResponse 对象并返回（钻石运算符<>，自动推断泛型）
        return new PageResponse<>(records, pageNum, pageSize, total, totalPages);
    }
}
