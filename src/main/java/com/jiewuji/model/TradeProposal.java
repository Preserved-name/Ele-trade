package com.jiewuji.model;

/**
 * 交易提案模型
 */
public record TradeProposal(
    Order order,                      // 订单
    double confidence,                // 置信度
    boolean isValid                   // 是否有效
) {}
