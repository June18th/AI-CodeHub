package com.aicodehub.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.aicodehub.mapper")
public class MyBatisPlusConfig {
}
