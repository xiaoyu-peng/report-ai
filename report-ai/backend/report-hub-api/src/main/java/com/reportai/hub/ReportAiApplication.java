package com.reportai.hub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan({
        "com.reportai.hub.common.mapper",
        "com.reportai.hub.user.mapper",
        "com.reportai.hub.role.mapper",
        "com.reportai.hub.department.mapper",
        "com.reportai.hub.knowledge.mapper",
        "com.reportai.hub.report.mapper"
})
public class ReportAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportAiApplication.class, args);
    }
}
