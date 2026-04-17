package com.jiewuji.model;

import java.util.List;

/**
 * 市场数据模型
 */
public record MarketData(
    String region,                    // 区域
    List<Double> loadProfile,         // 24点负荷曲线
    double lastPrice                  // 最新电价
) {}
