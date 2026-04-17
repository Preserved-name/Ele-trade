package com.jiwuji.model;

/**
 * 交易结果模型
 */
public record TradeResult(
    String sessionId,                 // 会话ID
    boolean success,                  // 是否成功
    String message,                   // 消息说明
    Order order                       // 订单（如果成功）
) {}
