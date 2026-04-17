package com.jiwuji.controller;

import com.jiwuji.agent.WorkflowOrchestrator;
import com.jiwuji.model.TradeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 交易控制器
 * 提供REST API接口用于触发交易工作流
 */
@Slf4j
@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
public class TradeController {

    private final WorkflowOrchestrator orchestrator;

    /**
     * 启动完整交易工作流（包含LLM策略生成）
     * 
     * @param sessionId 会话ID（可选，默认自动生成）
     * @return 交易结果
     */
    @PostMapping("/start")
    public Mono<TradeResult> startTrade(
            @RequestParam(required = false, defaultValue = "") String sessionId) {
        
        // 如果未提供sessionId，则自动生成
        if (sessionId.isEmpty()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            sessionId = "session_" + timestamp;
        }
        
        log.info("收到交易请求，会话ID: {}", sessionId);
        return orchestrator.executeFullWorkflow(sessionId);
    }

    /**
     * 启动审计测试模式（会触发审计拦截）
     * 
     * @param sessionId 会话ID（可选）
     * @return 交易结果
     */
    @PostMapping("/start-audit-test")
    public Mono<TradeResult> startAuditTest(
            @RequestParam(required = false, defaultValue = "") String sessionId) {
        
        if (sessionId.isEmpty()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            sessionId = "audit_test_" + timestamp;
        }
        
        log.info("收到审计测试请求，会话ID: {}", sessionId);
        return orchestrator.executeFullWorkflow(sessionId, true);
    }

    /**
     * 快速测试工作流（不使用LLM，仅测试数据流和风控）
     * 
     * @param sessionId 会话ID（可选）
     * @return 交易结果
     */
    @PostMapping("/test")
    public Mono<TradeResult> quickTest(
            @RequestParam(required = false, defaultValue = "") String sessionId) {
        
        if (sessionId.isEmpty()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            sessionId = "test_" + timestamp;
        }
        
        log.info("收到快速测试请求，会话ID: {}", sessionId);
        return orchestrator.executeQuickTest(sessionId);
    }

    /**
     * 健康检查接口
     * 
     * @return 系统状态
     */
    @GetMapping("/health")
    public Mono<String> health() {
        return Mono.just("AI电力交易系统运行中 - " + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
