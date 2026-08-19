package me.lj.train.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 学习与有效学时服务启动入口。
 */
@SpringBootApplication
@MapperScan("me.lj.train.learning.mapper")
public class TrainLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainLearningApplication.class, args);
    }
}
