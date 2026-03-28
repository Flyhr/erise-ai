package com.erise.ai.backend.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * MyBatis-Plus 配置类
 * 功能：1. 配置分页插件；2. 配置通用字段自动填充规则
 */
@Configuration
public class MybatisPlusConfig {
    /**
     * 注册MyBatis-Plus分页插件
     * @return 插件管理器（包含分页拦截器）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
         // 创建插件管理器实例
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页内部拦截器（自动适配数据库方言，如MySQL的LIMIT、Oracle的ROWNUM）
        // PaginationInnerInterceptor：拦截分页查询SQL，自动拼接分页条件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
    /**
     * 注册自定义元对象处理器，实现字段自动填充
     * MetaObjectHandler：MyBatis-Plus提供的扩展接口，拦截CRUD操作并填充指定字段
     * @return 自定义的元对象处理器
     */
    @Bean// 注册为Spring Bean，供MyBatis-Plus自动加载
    public MetaObjectHandler metaObjectHandler() {
        // 匿名内部类实现MetaObjectHandler接口，重写插入/更新填充方法
        return new MetaObjectHandler() {
            /**
             * 插入操作时的字段自动填充
             * 触发时机：执行mapper.insert(entity)时
             * @param metaObject 元对象，封装了实体类的属性和值
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                // strictInsertFill：严格填充（字段未手动赋值时才填充，避免覆盖业务值）
                // 参数：元对象、字段名、字段类型、填充值
                // 填充创建时间：插入时赋值为当前系统时间
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                // 填充更新时间：插入时与创建时间保持一致
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                 // 填充逻辑删除标记：插入时默认0（未删除）
                strictInsertFill(metaObject, "deleted", Integer.class, 0);
            }
            /**
             * 更新操作时的字段自动填充
             * 触发时机：执行mapper.updateById(entity)/update(entity, wrapper)时
             * @param metaObject 元对象，封装了实体类的属性和值
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                // strictUpdateFill：严格更新填充（字段未手动赋值时才填充）
                // 填充更新时间：更新时赋值为当前系统时间
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
