package com.example.im.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.example.common.mybatis.MybatisAutoFillHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置
 *
 * <p>特性：
 * <ul>
 *   <li>分页插件（MySQL）</li>
 *   <li>乐观锁（@Version 字段）</li>
 *   <li>自动填充（created_at / updated_at）</li>
 *   <li>逻辑删除（@TableLogic）</li>
 * </ul>
 */
@Configuration
public class MybatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /** 自动填充 created_at / updated_at（公共 handler） */
    @Bean
    public com.baomidou.mybatisplus.core.handlers.MetaObjectHandler metaObjectHandler() {
        return new MybatisAutoFillHandler();
    }
}