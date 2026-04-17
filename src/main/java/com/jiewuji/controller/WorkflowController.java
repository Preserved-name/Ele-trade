package com.jiewuji.controller;

import com.jiewuji.model.WorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流状态查询控制器
 * 提供工作流执行状态的查询接口
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 查询工作流状态
     * 
     * @param sessionId 会话ID
     * @return 工作流状态信息
     */
    @GetMapping("/status")
    public WorkflowState getStatus(@RequestParam String sessionId) {
        String redisKey = "wf:" + sessionId;
        WorkflowState state = (WorkflowState) redisTemplate.opsForValue().get(redisKey);
        
        if (state == null) {
            log.warn("未找到会话 {} 的状态信息", sessionId);
            return new WorkflowState(); // 返回空对象而不是null，避免前端解析问题
        } else {
            log.info("查询工作流状态 - Session: {}, Step: {}", sessionId, state.getCurrentStep());
            return state;
        }
    }

    /**
     * 删除工作流状态
     * 
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    @DeleteMapping("/status")
    public boolean deleteStatus(@RequestParam String sessionId) {
        String redisKey = "wf:" + sessionId;
        Boolean deleted = redisTemplate.delete(redisKey);
        log.info("删除工作流状态 - Session: {}, Result: {}", sessionId, deleted);
        return Boolean.TRUE.equals(deleted);
    }
}
