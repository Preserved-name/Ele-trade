package com.jiewuji.agent;

import com.jiewuji.model.ForecastResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 电价预测工具类
 * 基于Excel历史数据 + 大模型预测未来24小时电价
 */
@Slf4j
@Component
public class PriceForecastTool {

    private final ExcelDataReader excelDataReader;
    private final PricePredictor predictor;

    /**
     * 电价预测器接口（使用LangChain4j的AiServices）
     */
    interface PricePredictor {
        @SystemMessage("""
            你是一个电力交易价格预测专家。根据历史电价数据，预测未来24小时的电价走势。
            
            **数据格式说明：**
            - 时段格式：0-1时、1-2时、...、23-24时
            - 价格单位：元/兆瓦时
            - 每天24条记录，按时间顺序排列
            
            **预测要求：**
            1. 分析历史数据的日内波动规律（峰谷特征）
            2. 识别价格趋势（上升/下降/震荡）
            3. 考虑电力市场特性（早晚高峰价格较高，夜间较低）
            4. 预测未来24小时的成交均价
            
            **输出格式：**
            必须是严格的JSON格式，包含以下字段：
            - prices: 数组，24个预测价格值（对应0-1时到23-24时），保留2位小数
            - confidence: 数字，预测置信度（0-1之间），保留2位小数
            - trend: 字符串，整体趋势描述（"上升"、"下降"或"震荡"）
            - reasoning: 字符串，简要说明预测理由（100字以内）
            
            **示例输出：**
            {
              "prices": [401.34, 400.69, 398.05, 395.76, 392.41, 394.62, 402.81, 403.92, 407.54, 395.16, 384.92, 373.13, 369.36, 366.27, 367.78, 376.72, 381.02, 386.61, 392.56, 398.12, 385.80, 394.95, 398.81, 401.48],
              "confidence": 0.82,
              "trend": "震荡",
              "reasoning": "历史数据显示价格呈现典型日内峰谷特征，早晚高峰价格较高，夜间较低。整体价格区间在360-410元之间波动，预测未来走势保持震荡格局。"
            }
            
            **注意事项：**
            - 价格应该在合理范围内（通常 200-500 元/兆瓦时）
            - 置信度应根据历史数据的稳定性判断（数据波动小则置信度高）
            - 不要输出任何其他内容，只输出JSON。
            """)
        @UserMessage("""
            历史电价数据：
            {{historicalData}}
            
            请预测未来24小时的电价。
            """)
        String predictPrices(@V("historicalData") String historicalData);
    }

    public PriceForecastTool(ExcelDataReader excelDataReader, ChatLanguageModel chatModel) {
        this.excelDataReader = excelDataReader;
        this.predictor = AiServices.builder(PricePredictor.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    /**
     * 预测未来24小时电价
     * 
     * @param loadProfile 负荷曲线数据（从Excel导入的24小时电价数据）
     * @return 预测结果（24小时价格列表和置信度）
     */
    @Tool(description = "基于历史数据预测未来24小时电价，返回价格列表和置信度")
    public ForecastResult forecastPrices(List<Double> loadProfile) {
        try {
            log.info("开始电价预测...");
            
            // 1. 构建预测用的历史数据
            String historicalData;
            if (loadProfile != null && !loadProfile.isEmpty()) {
                // 使用传入的负荷曲线数据（已从Excel导入）
                log.info("使用传入的负荷曲线数据进行预测，数据点数: {}", loadProfile.size());
                historicalData = buildHistoricalDataFromLoadProfile(loadProfile);
            } else {
                // 如果没有传入数据，从Excel读取
                log.info("未传入负荷曲线，从Excel读取历史数据");
                historicalData = excelDataReader.getFormattedHistoricalData();
            }
            
            log.debug("历史数据: {}", historicalData);
            
            if (historicalData.equals("无历史数据")) {
                log.warn("无历史数据，使用默认预测");
                return fallbackForecast();
            }
            
            // 2. 调用LLM预测
            log.info("使用大模型进行电价预测...");
            String response = predictor.predictPrices(historicalData);
            log.debug("LLM预测响应: {}", response);
            
            // 3. 解析预测结果
            return parsePredictionResponse(response);
            
        } catch (Exception e) {
            log.error("电价预测失败，使用默认数据", e);
            // 降级：返回基于历史数据的简单统计
            return fallbackForecast();
        }
    }
    
    /**
     * 从负荷曲线数据构建历史数据字符串
     */
    private String buildHistoricalDataFromLoadProfile(List<Double> loadProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append("最新一天电价数据：\n");
        
        for (int i = 0; i < Math.min(24, loadProfile.size()); i++) {
            String timeSlot = String.format("%d-%d时", i, i + 1);
            double price = loadProfile.get(i);
            sb.append(String.format("时段: %s, 成交均价: %.2f 元/兆瓦时\n", timeSlot, price));
        }
        
        return sb.toString();
    }
    
    /**
     * 解析LLM预测响应
     */
    private ForecastResult parsePredictionResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
            
            // 提取价格列表
            List<Double> prices = new ArrayList<>();
            if (node.has("prices")) {
                for (com.fasterxml.jackson.databind.JsonNode priceNode : node.get("prices")) {
                    prices.add(priceNode.asDouble());
                }
            }
            
            // 提取置信度
            double confidence = 0.75;
            if (node.has("confidence")) {
                confidence = node.get("confidence").asDouble();
            }
            
            // 提取趋势
            String trend = "震荡";
            if (node.has("trend")) {
                trend = node.get("trend").asText();
            }
            
            // 提取推理说明
            String reasoning = "";
            if (node.has("reasoning")) {
                reasoning = node.get("reasoning").asText();
            }
            
            log.info("预测完成 - 趋势: {}, 置信度: {}, 均价: {:.2f}", 
                trend, 
                String.format("%.2f", confidence),
                prices.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            log.debug("预测详情: {}", reasoning);
            
            return new ForecastResult(prices, confidence);
            
        } catch (Exception e) {
            log.error("解析预测响应失败: {}", json, e);
            throw new RuntimeException("解析预测结果失败", e);
        }
    }
    
    /**
     * 降级预测：基于历史数据的简单统计
     */
    private ForecastResult fallbackForecast() {
        List<String> timeSlots = excelDataReader.getAllTimeSlots();
        List<Double> prices = new ArrayList<>();
        
        // 对每个时段，计算历史均价
        for (String timeSlot : timeSlots) {
            List<Double> historicalPrices = excelDataReader.getHistoricalPricesByTimeSlot(timeSlot);
            if (!historicalPrices.isEmpty()) {
                double avgPrice = historicalPrices.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(350.0);
                prices.add(avgPrice);
            }
        }
        
        // 如果没有数据，返回默认值
        if (prices.isEmpty()) {
            for (int i = 0; i < 24; i++) {
                prices.add(350.0);
            }
        }
        
        log.warn("使用降级预测模式，返回历史平均值");
        return new ForecastResult(prices, 0.60);
    }
}
