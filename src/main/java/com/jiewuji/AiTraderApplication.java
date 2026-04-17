package com.jiewuji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI电力交易辅助系统启动类
 */
@SpringBootApplication
public class AiTraderApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AiTraderApplication.class, args);
        System.out.println("========================================");
        System.out.println("  AI电力交易系统已启动");
        System.out.println("  API文档: http://localhost:8080/trade/health");
        System.out.println("========================================");
    }
}
