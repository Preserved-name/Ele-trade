package com.jiewuji.agent;

import com.jiewuji.model.MarketData;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 数据采集工具类
 * 负责从多源拉取并清洗市场数据
 */
@Component
public class DataCollectorTool {

    /**
     * 获取指定区域的实时负荷与电价数据
     * 
     * @param region 区域代码（如：SC-四川）
     * @return 市场数据
     */
    @Tool(description = "获取指定区域的实时负荷与电价数据")
    public MarketData fetchMarketData(String region) {
        // TODO: 实际实现中应调用真实的数据源API
        // 这里使用模拟数据
        Random rand = new Random();
        List<Double> loads = new ArrayList<>();
        
        // 生成24点负荷曲线（每小时一个点，模拟日负荷）
        for (int i = 0; i < 24; i++) {
            // 基础负荷5000MW + 随机波动
            double baseLoad = 5000 + 1000 * Math.sin(i * 2 * Math.PI / 24);
            loads.add(baseLoad + rand.nextInt(500));
        }
        
        double lastPrice = 320 + rand.nextDouble();
        
        return new MarketData(region, loads, lastPrice);
    }
}
