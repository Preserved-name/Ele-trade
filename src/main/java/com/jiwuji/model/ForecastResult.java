package com.jiwuji.model;

import java.util.List;

/**
 * 电价预测结果模型
 */
public record ForecastResult(
    List<Double> prices,              // 96点预测价格
    double confidence                 // 预测置信度 (0-1)
) {}
