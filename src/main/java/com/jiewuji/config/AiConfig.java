package com.jiewuji.config;

import dev.langchain4j.model.dashscope.QwenChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI配置类
 */
@Configuration
public class AiConfig {

    @Value("${ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${ai.dashscope.model-name:qwen-plus}")
    private String modelName;

    /**
     * Spring AI OpenAI Chat Client（使用通义千问兼容模式）
     * 注意：Spring AI目前主要通过OpenAI兼容接口调用，通义千问需要使用DashScope SDK
     */
    // @Bean
    // public OpenAiChatClient openAiChatClient() {
    //     return new OpenAiChatClient(new OpenAiApi(dashScopeBaseUrl, dashScopeApiKey));
    // }

    /**
     * LangChain4j 通义千问 Chat Model
     * 这是主要使用的模型配置
     */
    @Bean
    public QwenChatModel qwenChatModel() {
        return QwenChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(modelName)
                .temperature(0.1f)  // 低温度，保证输出稳定性
                .build();
    }
}
