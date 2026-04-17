package com.jiewuji.agent;

import com.jiewuji.model.Order;
import com.jiewuji.model.RiskDecision;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * 风控工具类
 * 校验订单是否违反风控规则
 */
@Component
public class RiskTool {

    private static final double MAX_PRICE = 500;      // 价格上限（元/kWh）
    private static final int MAX_VOLUME = 1000;        // 单笔电量上限（MWh）
    private static final double MIN_PRICE = 200;       // 价格下限（元/kWh）

    /**
     * 校验订单是否违反风控规则
     * 
     * @param order 待校验的订单
     * @return 风控决策结果
     */
    @Tool(description = "校验订单是否违反风控规则")
    public RiskDecision checkRisk(Order order) {
        // 规则1：检查单笔电量是否超限
        if (order.volume() > MAX_VOLUME) {
            return new RiskDecision(false, 
                String.format("单笔电量%d MWh超过上限%d MWh", order.volume(), MAX_VOLUME));
        }
        
        // 规则2：检查价格是否超过上限
        if (order.price() > MAX_PRICE) {
            return new RiskDecision(false, 
                String.format("价格%.4f元超过上限%.2f元", order.price(), MAX_PRICE));
        }
        
        // 规则3：检查价格是否低于下限
        if (order.price() < MIN_PRICE) {
            return new RiskDecision(false, 
                String.format("价格%.4f元低于下限%.2f元", order.price(), MIN_PRICE));
        }
        
        // 规则4：检查交易方向是否合法
        if (!"BUY".equalsIgnoreCase(order.direction()) && 
            !"SELL".equalsIgnoreCase(order.direction())) {
            return new RiskDecision(false, 
                String.format("无效的交易方向: %s", order.direction()));
        }
        
        // 规则5：电量和价格必须为正数
        if (order.volume() <= 0 || order.price() <= 0) {
            return new RiskDecision(false, "电量和价格必须为正数");
        }
        
        // TODO: 实际实现中应增加更多风控规则，如：
        // - 账户余额检查
        // - 持仓限额检查
        // - 日内交易次数限制
        // - 市场流动性检查
        
        return new RiskDecision(true, "通过所有风控检查");
    }
}
