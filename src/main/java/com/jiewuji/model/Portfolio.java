package com.jiewuji.model;

/**
 * 投资组合模型
 */
public record Portfolio(
    double cash,                      // 现金余额
    int position                      // 当前持仓(MWh)
) {
    @Override
    public String toString() {
        return String.format("{\"cash\":%.2f,\"position\":%d}", cash, position);
    }
}
