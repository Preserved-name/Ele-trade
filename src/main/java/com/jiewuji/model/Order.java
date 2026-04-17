package com.jiewuji.model;

/**
 * 交易订单模型
 */
public record Order(
    String direction,                 // BUY/SELL
    double price,                     // 价格
    int volume,                       // 电量(MWh)
    String sessionId                  // 会话ID
) {}
