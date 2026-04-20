package com.jiewuji.controller;

import com.jiewuji.agent.WorkflowOrchestrator;
import com.jiewuji.model.TradeResult;
import dev.langchain4j.model.dashscope.QwenChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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
    private final QwenChatModel qwenChatModel;

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

    /**
     * 查询当前使用的大模型信息
     * 
     * @return 模型配置信息
     */
    @GetMapping("/model-info")
    public Mono<Map<String, Object>> getModelInfo() {
        Map<String, Object> modelInfo = new HashMap<>();
        
        try {
            // 从配置中获取模型名称（通过反射或配置文件）
            String modelName = "qwen-plus"; // 默认值
            
            // 尝试从QwenChatModel获取实际配置的模型名
            try {
                java.lang.reflect.Field modelNameField = qwenChatModel.getClass().getDeclaredField("modelName");
                modelNameField.setAccessible(true);
                modelName = (String) modelNameField.get(qwenChatModel);
            } catch (Exception e) {
                log.warn("无法通过反射获取模型名称，使用默认值", e);
            }
            
            modelInfo.put("success", true);
            modelInfo.put("provider", "阿里云通义千问 (DashScope)");
            modelInfo.put("modelName", modelName);
            modelInfo.put("framework", "LangChain4j");
            modelInfo.put("temperature", 0.1);
            modelInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            log.info("查询模型信息 - 提供商: {}, 模型: {}", "阿里云通义千问", modelName);
            
        } catch (Exception e) {
            log.error("获取模型信息失败", e);
            modelInfo.put("success", false);
            modelInfo.put("error", "获取模型信息失败: " + e.getMessage());
        }
        
        return Mono.just(modelInfo);
    }

    /**
     * 与大模型直接对话
     * 
     * @param request 对话请求，包含用户消息
     * @return 模型回复
     */
    @PostMapping("/chat")
    public Mono<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String userMessage = request.get("message");
            
            if (userMessage == null || userMessage.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "消息不能为空");
                return Mono.just(response);
            }
            
            log.info("收到用户消息: {}", userMessage);
            
            // 调用大模型
            String aiResponse = qwenChatModel.generate(userMessage);
            
            log.info("AI回复: {}", aiResponse);
            
            response.put("success", true);
            response.put("message", aiResponse);
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
        } catch (Exception e) {
            log.error("对话失败", e);
            response.put("success", false);
            response.put("error", "对话失败: " + e.getMessage());
        }
        
        return Mono.just(response);
    }
}
