package com.jiewuji.model;

/**
 * 风控决策模型
 */
public record RiskDecision(
    boolean allowed,                  // 是否允许
    String reason                     // 原因说明
) {}
