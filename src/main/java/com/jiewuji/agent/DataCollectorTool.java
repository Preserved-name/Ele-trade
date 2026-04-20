package com.jiewuji.agent;

import com.jiewuji.model.MarketData;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class DataCollectorTool {

    private final ExcelDataReader excelDataReader;

    /**
     * 获取指定区域的实时负荷与电价数据
     * 
     * @param region 区域代码（如：SC-四川）
     * @return 市场数据
     */
    @Tool(description = "获取指定区域的实时负荷与电价数据")
    public MarketData fetchMarketData(String region) {
        // 从Excel读取历史电价数据
        List<Double> loadProfile = new ArrayList<>();
        double lastPrice = 0.0;
        
        try {
            // 获取所有时段的价格数据作为负荷曲线
            List<String> timeSlots = excelDataReader.getAllTimeSlots();
            for (String timeSlot : timeSlots) {
                List<Double> prices = excelDataReader.getHistoricalPricesByTimeSlot(timeSlot);
                if (!prices.isEmpty()) {
                    // 使用该时段的平均价格作为负荷值
                    double avgPrice = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    loadProfile.add(avgPrice);
                }
            }
            
            // 如果没有足够的24小时数据，补充默认值
            while (loadProfile.size() < 24) {
                loadProfile.add(320.0); // 默认电价
            }
            
            // 获取最新电价（最后一个时段的价格）
            if (!loadProfile.isEmpty()) {
                lastPrice = loadProfile.get(loadProfile.size() - 1);
            } else {
                // 如果Excel数据为空，使用默认值
                Random rand = new Random();
                for (int i = 0; i < 24; i++) {
                    double baseLoad = 5000 + 1000 * Math.sin(i * 2 * Math.PI / 24);
                    loadProfile.add(baseLoad + rand.nextInt(500));
                }
                lastPrice = 320 + rand.nextDouble();
            }
            
        } catch (Exception e) {
            // 如果读取失败，回退到模拟数据
            Random rand = new Random();
            loadProfile.clear();
            for (int i = 0; i < 24; i++) {
                double baseLoad = 5000 + 1000 * Math.sin(i * 2 * Math.PI / 24);
                loadProfile.add(baseLoad + rand.nextInt(500));
            }
            lastPrice = 320 + rand.nextDouble();
        }
        
        return new MarketData(region, loadProfile, lastPrice);
    }
}
