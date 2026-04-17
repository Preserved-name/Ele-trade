package com.jiwuji.model;

import lombok.Data;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作流状态模型
 * 用于记录和查询多Agent协作流程的执行状态
 */
@Data
public class WorkflowState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String sessionId;                    // 会话ID
    private String currentStep;                  // 当前执行步骤
    private Map<String, Object> data = new HashMap<>();  // 步骤数据
    
    /**
     * 获取步骤数据的便捷方法
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> clazz) {
        Object value = data.get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}
