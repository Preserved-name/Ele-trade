package com.jiwuji.agent;

import com.jiwuji.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 工作流编排器
 * 负责编排多Agent协作流程：采集 → 预测 → 策略 → 风控 → 执行
 * 支持状态持久化和断点续跑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowOrchestrator {

    private final DataCollectorTool dataCollector;
    private final PriceForecastTool priceForecast;
    private final RiskTool riskTool;
    private final StrategyAgent strategyAgent;
    private final AuditAgent auditAgent;
    private final RedisTemplate<String, Object> redisTemplate;

    // 置信度阈值
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    /**
     * 执行完整的工作流（带状态持久化）
     * 
     * @param sessionId 会话ID
     * @return 交易结果
     */
    public Mono<TradeResult> executeFullWorkflow(String sessionId) {
        return executeFullWorkflow(sessionId, false);
    }

    /**
     * 执行完整的工作流（带状态持久化，支持测试模式）
     * 
     * @param sessionId 会话ID
     * @param testAuditMode 是否启用审计测试模式（生成异常价格触发审计拦截）
     * @return 交易结果
     */
    public Mono<TradeResult> executeFullWorkflow(String sessionId, boolean testAuditMode) {
        WorkflowState state = new WorkflowState();
        state.setSessionId(sessionId);
        
        return Mono.fromCallable(() -> {
            log.info("========== 开始工作流 [{}] ==========", sessionId);
            
            // Step 1: 采集市场数据
            log.info("[Step 1/6] 采集市场数据...");
            var marketData = dataCollector.fetchMarketData("SC");
            updateState(state, "DATA_COLLECTED", "lastPrice", marketData.lastPrice());
            log.info("采集完成，区域: {}, 最新电价: {}元", 
                marketData.region(), String.format("%.4f", marketData.lastPrice()));

            // Step 2: 电价预测
            log.info("[Step 2/6] 进行电价预测...");
            var forecast = priceForecast.forecastPrices(marketData.loadProfile());
            updateState(state, "FORECAST_DONE", "confidence", forecast.confidence());
            log.info("预测完成，置信度: {}", String.format("%.2f", forecast.confidence()));

            // Step 3: 低置信度分支处理（降级策略）
            if (forecast.confidence() < CONFIDENCE_THRESHOLD) {
                log.warn("[降级] 置信度{}低于阈值{}，触发人工决策分支", 
                    String.format("%.2f", forecast.confidence()), CONFIDENCE_THRESHOLD);
                updateState(state, "FALLBACK", "reason", "低置信度");
                return new TradeResult(
                    sessionId, 
                    false, 
                    String.format("置信度不足(%.2f)，转为人工决策", forecast.confidence()), 
                    null
                );
            }

            // Step 4: 生成交易策略
            log.info("[Step 4/6] 生成交易策略...");
            Portfolio portfolio = new Portfolio(1_000_000, 2000);  // 模拟持仓：100万现金，2000MWh持仓
            var proposal = strategyAgent.propose(forecast, portfolio);

            if (!proposal.isValid() || proposal.order() == null) {
                log.warn("策略生成失败或跳过");
                updateState(state, "FAILED", "reason", "策略失败");
                return new TradeResult(sessionId, false, "策略生成失败", null);
            }
            
            // 测试模式：人为篡改价格，制造幻觉订单
            if (testAuditMode) {
                Order originalOrder = proposal.order();
                // 将价格篡改为预测最高价的3倍，明显超出合理范围
                double maxPrice = forecast.prices().stream().mapToDouble(Double::doubleValue).max().orElse(0.5);
                double anomalousPrice = maxPrice * 3.0;
                Order tamperedOrder = new Order(
                    originalOrder.direction(),
                    anomalousPrice,
                    originalOrder.volume(),
                    originalOrder.sessionId()
                );
                proposal = new com.jiwuji.model.TradeProposal(tamperedOrder, proposal.confidence(), proposal.isValid());
                log.warn("[测试模式] 人为篡改订单价格: {} -> {} (用于测试审计拦截)", 
                    originalOrder.price(), anomalousPrice);
            }
            
            log.info("策略提案: {} {} MWh @ {}元", 
                proposal.order().direction(),
                proposal.order().volume(),
                String.format("%.4f", proposal.order().price()));
            updateState(state, "STRATEGY_DONE", "strategy", proposal);

            // Step 4.5: 审计 Agent 防幻觉校验
            log.info("[Step 4.5/6] 执行审计 Agent 防幻觉校验...");
            boolean auditPassed = auditAgent.verify(proposal, forecast.prices());
            updateState(state, "AUDIT_DONE", "auditPassed", auditPassed);
            
            if (!auditPassed) {
                log.warn("[审计拦截] 策略未通过审计，判定为幻觉订单");
                updateState(state, "AUDIT_REJECTED", "reason", "审计拦截");
                return new TradeResult(sessionId, false, "审计拦截：策略价格异常，疑似AI幻觉", null);
            }
            
            log.info("审计通过");

            // Step 5: 风控校验
            log.info("[Step 5/6] 执行风控检查...");
            var riskDecision = riskTool.checkRisk(proposal.order());
            updateState(state, "RISK_CHECK", "risk", riskDecision);
            
            if (!riskDecision.allowed()) {
                log.warn("[风控拦截] {}", riskDecision.reason());
                updateState(state, "REJECTED", "reason", riskDecision.reason());
                return new TradeResult(
                    sessionId, 
                    false, 
                    "风控拦截: " + riskDecision.reason(), 
                    null
                );
            }
            
            log.info("风控通过: {}", riskDecision.reason());

            // Step 6: 执行订单（模拟）
            log.info("[Step 6/6] 执行订单...");
            Order executedOrder = new Order(
                proposal.order().direction(),
                proposal.order().price(),
                proposal.order().volume(),
                sessionId
            );
            updateState(state, "EXECUTED", "order", executedOrder);
            
            log.info("订单已提交: {}", executedOrder);
            log.info("========== 工作流完成 [{}] ==========", sessionId);
            
            return new TradeResult(sessionId, true, "订单已成功提交", executedOrder);
            
        }).onErrorResume(e -> {
            log.error("工作流执行异常", e);
            updateState(state, "ERROR", "exception", e.getMessage());
            return Mono.just(new TradeResult(sessionId, false, "系统异常: " + e.getMessage(), null));
        });
    }

    /**
     * 快速测试工作流（不使用LLM，仅测试数据流）
     * 
     * @param sessionId 会话ID
     * @return 交易结果
     */
    public Mono<TradeResult> executeQuickTest(String sessionId) {
        WorkflowState state = new WorkflowState();
        state.setSessionId(sessionId);
        
        return Mono.fromCallable(() -> {
            log.info("========== 快速测试工作流 [{}] ==========", sessionId);
            
            // Step 1: 采集数据
            var marketData = dataCollector.fetchMarketData("SC");
            updateState(state, "DATA_COLLECTED", "lastPrice", marketData.lastPrice());
            log.info("数据采集完成");

            // Step 2: 预测
            var forecast = priceForecast.forecastPrices(marketData.loadProfile());
            updateState(state, "FORECAST_DONE", "confidence", forecast.confidence());
            log.info("预测完成，置信度: {}", String.format("%.2f", forecast.confidence()));

            // Step 3: 检查置信度
            if (forecast.confidence() < CONFIDENCE_THRESHOLD) {
                updateState(state, "FALLBACK", "reason", "低置信度");
                return new TradeResult(sessionId, false, "置信度不足", null);
            }

            // Step 4: 创建模拟订单（不调用LLM）
            Order order = new Order("BUY", 0.45, 500, sessionId);
            updateState(state, "STRATEGY_DONE", "order", order);
            log.info("创建测试订单: {}", order);

            // Step 5: 风控检查
            var riskDecision = riskTool.checkRisk(order);
            updateState(state, "RISK_CHECK", "risk", riskDecision);
            if (!riskDecision.allowed()) {
                updateState(state, "REJECTED", "reason", riskDecision.reason());
                return new TradeResult(sessionId, false, "风控拦截: " + riskDecision.reason(), null);
            }

            updateState(state, "EXECUTED", "order", order);
            log.info("========== 快速测试完成 [{}] ==========", sessionId);
            return new TradeResult(sessionId, true, "测试订单通过风控", order);
            
        }).onErrorResume(e -> {
            log.error("快速测试异常", e);
            updateState(state, "ERROR", "exception", e.getMessage());
            return Mono.just(new TradeResult(sessionId, false, "异常: " + e.getMessage(), null));
        });
    }

    /**
     * 更新工作流状态并持久化到Redis
     * 
     * @param state 工作流状态对象
     * @param step 当前步骤名称
     * @param key 数据键
     * @param value 数据值
     */
    private void updateState(WorkflowState state, String step, String key, Object value) {
        state.setCurrentStep(step);
        state.getData().put(key, value);
        
        // 持久化到Redis，设置过期时间为24小时
        String redisKey = "wf:" + state.getSessionId();
        redisTemplate.opsForValue().set(redisKey, state, java.time.Duration.ofHours(24));
        
        log.debug("状态更新 - Session: {}, Step: {}, Key: {}", state.getSessionId(), step, key);
    }
}
