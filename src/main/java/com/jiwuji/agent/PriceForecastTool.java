package com.jiwuji.agent;

import com.jiwuji.model.ForecastResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 电价预测工具类
 * 基于负荷和天气数据预测未来96点电价
 */
@Component
public class PriceForecastTool {

    /**
     * 基于负荷预测未来96点电价
     * 
     * @param loadProfile 96点负荷曲线
     * @return 预测结果（价格列表和置信度）
     */
    @Tool(description = "基于负荷和天气预测未来96点电价，返回价格列表和置信度")
    public ForecastResult forecastPrices(List<Double> loadProfile) {
        // TODO: 实际实现中应调用训练好的机器学习模型
        // 这里使用简化的模拟算法
        
        List<Double> prices = new ArrayList<>();
        
        // 模拟电价：基础价格 + 正弦波动（反映日内峰谷） + 随机噪声
        for (int i = 0; i < 96; i++) {
            double basePrice = 0.4;  // 基础电价 0.4元/kWh
            double peakFactor = 0.1 * Math.sin(i * 2 * Math.PI / 96);  // 峰谷因子
            double noise = (Math.random() - 0.5) * 0.04;  // 随机噪声
            
            double price = basePrice + peakFactor + noise;
            prices.add(Math.max(0.2, price));  // 确保价格为正
        }
        
        // 模拟置信度（0.65-0.95之间）
        double confidence = 0.65 + Math.random() * 0.3;
        
        return new ForecastResult(prices, confidence);
    }
}
