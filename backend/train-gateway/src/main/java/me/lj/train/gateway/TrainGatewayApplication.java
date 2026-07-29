package me.lj.train.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一接入网关启动入口。
 */
@SpringBootApplication
public class TrainGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainGatewayApplication.class, args);
    }
}
