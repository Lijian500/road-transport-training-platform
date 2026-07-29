package me.lj.train.webapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * REST接口与前端业务聚合服务启动入口。
 */
@SpringBootApplication
public class TrainWebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainWebApiApplication.class, args);
    }
}
