package me.lj.train.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理基础服务启动入口。
 */
@SpringBootApplication
@MapperScan("me.lj.train.admin.mapper")
public class TrainAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainAdminApplication.class, args);
    }
}
