package com.jiwuji.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiwuji.model.ForecastResult;
import com.jiwuji.model.Order;
import com.jiwuji.model.Portfolio;
import com.jiwuji.model.TradeProposal;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 策略Agent
 * 基于预测结果和持仓情况生成交易策略
 */
@Slf4j
@Service
public class StrategyAgent {

    private final StrategyGenerator generator;
    private final ObjectMapper objectMapper;

    /**
     * 策略生成器接口（使用LangChain4j的AiServices）
     */
    interface StrategyGenerator {
        @SystemMessage("""
            你是一个电力交易策略专家。根据预测电价走势和当前持仓，生成最优的交易订单。
            
            **核心原则：分批交易，控制风险**
            - 永远不要一次性全仓买入或卖出
            - 每次交易量控制在当前持仓或可用资金的20%-40%
            - 保留足够的缓冲仓位应对市场波动
            
            **风控红线（必须严格遵守）：**
            - 价格下限：BUY/SELL价格不得低于 0.10 元/kWh
            - 价格上限：BUY/SELL价格不得高于 1.20 元/kWh
            - 电量下限：单次交易量不得低于 100 MWh
            - 电量上限：单次交易量不得高于 500 MWh
            - 价格合理性：BUY价格不应显著高于当前市场价20%以上，SELL价格不应显著低于当前市场价20%以下
            
            **交易策略逻辑：**
            
            1. **判断趋势**：
               - 分析96点价格列表，判断整体趋势（上升/下降/震荡）
               - 找出价格最低点和最高点的时间段
            
            2. **BUY策略（建仓）**：
               - 触发条件：预测价格上升 OR 当前持仓 < 1500 MWh
               - 交易量：建议 200-400 MWh（小额试探性建仓）
               - 价格选择：选择预测价格的前30%低位区间
               - 典型场景：
                 * 持仓很低时 → 适度建仓（300-400 MWh）
                 * 持仓适中时 → 小幅加仓（200-300 MWh）
                 * 已经高持仓 → 不BUY或极小量（<200 MWh）
            
            3. **SELL策略（减仓）**：
               - 触发条件：预测价格下降 OR 当前持仓 > 2500 MWh
               - 交易量：建议 200-400 MWh（分批止盈/止损）
               - 价格选择：选择预测价格的前30%高位区间
               - 典型场景：
                 * 持仓很高时 → 适度减仓（300-400 MWh）
                 * 持仓适中时 → 小幅减仓（200-300 MWh）
                 * 已经低持仓 → 不SELL或极小量（<200 MWh）
            
            4. **HOLD策略（观望）**：
               - 触发条件：价格震荡不明朗 OR 持仓已在合理区间（1500-2500 MWh）
               - 可以选择小量BUY或SELL进行调仓，或者跳过
            
            **仓位管理规则：**
            - 当前持仓 < 1000 MWh：偏BUY，但单次不超过400 MWh
            - 当前持仓 1000-2000 MWh：平衡策略，根据趋势小幅调整（200-300 MWh）
            - 当前持仓 2000-3000 MWh：偏SELL，但单次不超过400 MWh
            - 当前持仓 > 3000 MWh：强烈建议SELL减仓，但也不要超过500 MWh
            
            **输出格式：**
            必须是严格的JSON格式，包含以下字段：
            - direction: "BUY" 或 "SELL"（必须二选一，如果不确定选更接近的）
            - price: 数字类型，表示价格（元/kWh），保留4位小数，必须在0.10-1.20范围内
            - volume: 整数类型，表示电量（MWh），范围100-500（严禁超过500）
            - reason: 字符串类型，简要说明策略理由和仓位考虑（50字以内）
            
            **特殊情况：**
            如果预测置信度低于0.6，请输出：{"action":"skip","reason":"置信度不足"}
            
            **示例输出：**
            {"direction":"BUY","price":0.3850,"volume":300,"reason":"预测价格上涨，当前持仓较低，适度建仓"}
            {"direction":"SELL","price":0.4920,"volume":250,"reason":"预测价格下跌，高位分批减仓避险"}
            {"direction":"BUY","price":0.4100,"volume":200,"reason":"震荡行情，小幅补仓降低成本"}
            
            **重要提醒：**
            - 交易量宁可保守，不要激进
            - 始终考虑当前持仓水平
            - 目标是渐进式调整仓位，不是一步到位
            - **生成订单前务必检查：价格在0.10-1.20之间，电量在100-500之间**
            
            不要输出任何其他内容，只输出JSON。
            """)
        @UserMessage("预测价格列表: {{prices}}，最新置信度: {{confidence}}，当前持仓: {{portfolio}}")
        String generateStrategy(
            @V("prices") String pricesJson,
            @V("confidence") double confidence,
            @V("portfolio") String portfolio
        );
    }

    public StrategyAgent(QwenChatModel model) {
        this.objectMapper = new ObjectMapper();
        this.generator = AiServices.builder(StrategyGenerator.class)
                .chatLanguageModel(model)
                .build();
    }

    /**
     * 生成交易提案
     * 
     * @param forecast 预测结果
     * @param portfolio 当前投资组合
     * @return 交易提案
     */
    public TradeProposal propose(ForecastResult forecast, Portfolio portfolio) {
        try {
            // 将价格列表转换为带时间点的格式
            String pricesWithTime = formatPricesWithTime(forecast.prices());
            
            // 调用LLM生成策略
            String response = generator.generateStrategy(
                pricesWithTime,
                forecast.confidence(),
                portfolio.toString()
            );
            
            log.debug("策略Agent原始响应: {}", response);
            
            // 解析响应并包装为TradeProposal
            return parseResponse(response, forecast.confidence());
            
        } catch (Exception e) {
            log.error("策略生成失败", e);
            return new TradeProposal(null, forecast.confidence(), false);
        }
    }

    /**
     * 将96点价格列表格式化为带时间点的字符串
     * 每15分钟一个点，从00:00到23:45
     */
    private String formatPricesWithTime(java.util.List<Double> prices) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        // 只显示关键时间点：整点和半点，避免日志过长
        int[] showIndexes = {0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 
                             48, 52, 56, 60, 64, 68, 72, 76, 80, 84, 88, 92};
        
        for (int i : showIndexes) {
            if (i < prices.size()) {
                int hour = i / 4;
                int minute = (i % 4) * 15;
                String time = String.format("%02d:%02d", hour, minute);
                double price = prices.get(i);
                
                if (sb.length() > 1) sb.append(", ");
                sb.append(String.format("{\"time\":\"%s\",\"price\":%.4f}", time, price));
            }
        }
        
        sb.append("]");
        return sb.toString();
    }

    /**
     * 解析LLM响应
     * 
     * @param json LLM返回的JSON字符串
     * @param forecastConfidence 预测置信度
     * @return 交易提案
     */
    private TradeProposal parseResponse(String json, double forecastConfidence) {
        try {
            JsonNode node = objectMapper.readTree(json);
            
            // 检查是否为跳过动作
            if (node.has("action") && "skip".equals(node.get("action").asText())) {
                String reason = node.has("reason") ? node.get("reason").asText() : "未知原因";
                log.info("策略跳过: {}", reason);
                return new TradeProposal(null, forecastConfidence, false);
            }
            
            // 解析订单信息
            String direction = node.get("direction").asText();
            double price = node.get("price").asDouble();
            int volume = node.get("volume").asInt();
            String reason = node.has("reason") ? node.get("reason").asText() : "";
            
            Order order = new Order(direction, price, volume, "");
            
            log.info("生成交易策略: {} | 电量: {} MWh | 价格: {}元/kWh | 原因: {}",
                direction, volume, String.format("%.4f", price), reason);
            
            return new TradeProposal(order, forecastConfidence, true);
            
        } catch (JsonProcessingException e) {
            log.error("解析策略响应失败: {}", json, e);
            return new TradeProposal(null, forecastConfidence, false);
        }
    }
}
