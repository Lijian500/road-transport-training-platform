package me.lj.train.training.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 对象存储配置入口。
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OssStorageProperties.class)
public class StorageConfiguration {
}
