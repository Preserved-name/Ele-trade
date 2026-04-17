package com.jiewuji.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiewuji.model.Order;
import com.jiewuji.model.TradeProposal;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计 Agent (方案4核心亮点)
 * 负责防止"幻觉传染"，校验策略的合理性
 */
@Slf4j
@Service
public class AuditAgent {

    private final AuditVerifier verifier;
    private final ObjectMapper objectMapper;

    /**
     * 审计验证器接口（使用LangChain4j的AiServices）
     */
    interface AuditVerifier {
        @SystemMessage("""
            你是一个电力交易审计员，专门负责检测AI生成的交易策略是否存在"幻觉"问题。
            
            **审计职责：**
            1. 检查策略价格是否在预测价格的合理范围内
            2. 判断策略方向是否符合市场趋势
            3. 识别可能的AI幻觉或异常决策
            
            **审计规则：**
            - 价格合理性：策略价格应该在预测价格区间的 [min*0.8, max*1.2] 范围内
            - 如果价格超出此范围，判定为"幻觉订单"，必须拦截
            - 如果价格在范围内，但偏离预测均值超过30%，需要谨慎评估
            
            **输出格式：**
            必须是严格的JSON格式，包含以下字段：
            - passed: 布尔值，true表示通过审计，false表示拦截
            - reason: 字符串类型，说明审计结果和理由（50字以内）
            - riskLevel: 字符串类型，风险等级（"LOW"/"MEDIUM"/"HIGH"）
            
            **示例输出：**
            {"passed":true,"reason":"策略价格在预测区间内，符合市场趋势","riskLevel":"LOW"}
            {"passed":false,"reason":"策略价格0.9元远超预测最高价0.5元，疑似AI幻觉","riskLevel":"HIGH"}
            {"passed":false,"reason":"策略价格0.15元远低于预测最低价0.35元，明显不合理","riskLevel":"HIGH"}
            
            不要输出任何其他内容，只输出JSON。
            """)
        @UserMessage("""
            当前预测电价区间：{{minPrice}} - {{maxPrice}} 元
            AI生成的交易策略：{{direction}} {{volume}} MWh @ {{price}} 元
            
            请审计该策略是否合理，是否存在AI幻觉问题？
            """)
        String verifyStrategy(
            @V("minPrice") double minPrice,
            @V("maxPrice") double maxPrice,
            @V("direction") String direction,
            @V("volume") int volume,
            @V("price") double price
        );
    }

    public AuditAgent(QwenChatModel model) {
        this.objectMapper = new ObjectMapper();
        this.verifier = AiServices.builder(AuditVerifier.class)
                .chatLanguageModel(model)
                .build();
    }

    /**
     * 审计交易提案
     * 
     * @param proposal 交易提案
     * @param forecastPrices 预测价格列表（用于比对）
     * @return 是否通过审计
     */
    public boolean verify(TradeProposal proposal, List<Double> forecastPrices) {
        if (proposal.order() == null) {
            log.info("[审计] 无订单信息，跳过审计");
            return true;
        }

        Order order = proposal.order();
        
        // 1. 基础逻辑校验：价格是否在预测区间内
        double minPrice = forecastPrices.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxPrice = forecastPrices.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        
        log.info("[审计] 预测价格区间: [{}, {}], 策略价格: {}",
            minPrice, maxPrice, order.price());
        
        // 如果策略价格偏离预测区间超过 20%，判定为幻觉
        if (order.price() < minPrice * 0.8 || order.price() > maxPrice * 1.2) {
            log.warn("[审计拦截] 策略价格 {} 超出预测区间 [{}, {}] 的20%容差范围",
                order.price(), minPrice, maxPrice);
            return false;
        }

        // 2. LLM 语义审计
        try {
            String response = verifier.verifyStrategy(
                minPrice,
                maxPrice,
                order.direction(),
                order.volume(),
                order.price()
            );
            
            log.info("[审计] LLM 审计响应: {}", response);
            
            // 解析审计结果
            AuditResult result = parseAuditResponse(response);
            
            if (!result.passed) {
                log.warn("[审计拦截] LLM审计未通过 - 风险等级: {}, 原因: {}", 
                    result.riskLevel, result.reason);
            } else {
                log.info("[审计通过] 风险等级: {}, 原因: {}", result.riskLevel, result.reason);
            }
            
            return result.passed;
            
        } catch (Exception e) {
            log.error("[审计] LLM 审计异常，默认放行", e);
            return true;
        }
    }

    /**
     * 解析审计响应
     */
    private AuditResult parseAuditResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            
            boolean passed = node.has("passed") && node.get("passed").asBoolean();
            String reason = node.has("reason") ? node.get("reason").asText() : "未知原因";
            String riskLevel = node.has("riskLevel") ? node.get("riskLevel").asText() : "UNKNOWN";
            
            return new AuditResult(passed, reason, riskLevel);
            
        } catch (JsonProcessingException e) {
            log.error("[审计] 解析审计响应失败: {}", json, e);
            // 解析失败时默认放行，避免阻塞正常流程
            return new AuditResult(true, "解析失败，默认放行", "UNKNOWN");
        }
    }

    /**
     * 审计结果内部类
     */
    private record AuditResult(boolean passed, String reason, String riskLevel) {}
}
