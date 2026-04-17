package com.jiewuji.agent;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * ONNX 模型推理引擎
 * 负责加载和运行 LightGBM 导出的 ONNX 模型进行电价预测
 */
@Slf4j
@Component
public class OnnxModelEngine {

    @Value("${model.path:classpath:models/adv_inception_v3_Opset16.onnx}")
    private Resource modelResource;

    @Value("${model.input.size:24}")
    private int inputSize;

    private OrtSession session;
    private OrtEnvironment environment;

    /**
     * 初始化：加载 ONNX 模型
     */
    @PostConstruct
    public void init() {
        try {
            log.info("正在初始化 ONNX Runtime 引擎...");
            
            // 创建环境（单例，线程安全）
            environment = OrtEnvironment.getEnvironment();
            
            // 加载模型文件
            byte[] modelBytes = modelResource.getInputStream().readAllBytes();
            
            // 创建会话选项
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            
            // 创建推理会话
            session = environment.createSession(modelBytes, options);
            
            log.info("ONNX 模型加载成功 - 输入节点数: {}, 输出节点数: {}", 
                session.getInputNames().size(), 
                session.getOutputNames().size());
            
            // 打印模型元信息
            log.info("输入节点数量: {}", session.getInputNames().size());
            session.getInputNames().forEach(name -> 
                log.info("  - 输入节点: {}", name));
            
            log.info("输出节点数量: {}", session.getOutputNames().size());
            session.getOutputNames().forEach(name -> 
                log.info("  - 输出节点: {}", name));
            
        } catch (OrtException | IOException e) {
            log.error("ONNX 模型加载失败，将使用模拟预测模式", e);
            throw new RuntimeException("ONNX 模型初始化失败", e);
        }
    }

    /**
     * 执行电价预测推理
     * 
     * @param loadProfile 24点负荷曲线
     * @return 24点预测电价
     */
    public List<Double> predict(List<Double> loadProfile) {
        if (session == null) {
            log.warn("ONNX 会话未初始化，返回空结果");
            return Collections.emptyList();
        }

        try {
            // 1. 数据预处理：转换为 float 数组
            float[] inputData = new float[loadProfile.size()];
            for (int i = 0; i < loadProfile.size(); i++) {
                inputData[i] = loadProfile.get(i).floatValue();
            }

            // 2. 验证输入维度
            if (inputData.length != inputSize) {
                throw new IllegalArgumentException(
                    String.format("输入维度不匹配: 期望 %d, 实际 %d", inputSize, inputData.length));
            }

            // 3. 创建 ONNX Tensor
            // 模型期望 4 维输入: [batch_size, channels, height, width] 或 [batch_size, 1, seq_len, features]
            // 对于 24 点负荷曲线，使用形状 [1, 1, 24, 1]
            long[] shape = {1, 1, inputSize, 1};  // [batch, channel, length, feature]
            log.debug("创建输入 Tensor - 形状: {}", java.util.Arrays.toString(shape));
            
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, 
                FloatBuffer.wrap(inputData), shape);

            // 4. 执行推理
            Map<String, OnnxTensor> inputs = Collections.singletonMap(
                session.getInputNames().iterator().next(), inputTensor);
            
            OrtSession.Result result = session.run(inputs);

            // 5. 提取输出
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            Object outputValue = outputTensor.getValue();
            
            log.debug("输出类型: {}, 值类型: {}", 
                outputTensor.getInfo().getClass().getSimpleName(),
                outputValue.getClass().getName());
            
            // 根据输出类型灵活处理
            List<Double> priceList;
            if (outputValue instanceof float[][]) {
                // 2D 输出: [batch, seq_len]
                float[][] predictions = (float[][]) outputValue;
                priceList = new ArrayList<>();
                for (float price : predictions[0]) {
                    priceList.add((double) price);
                }
            } else if (outputValue instanceof float[][][][]) {
                // 4D 输出: [batch, channel, seq_len, feature]
                float[][][][] predictions = (float[][][][]) outputValue;
                priceList = new ArrayList<>();
                // 遍历 [0][0][time_step][0] 提取所有时间点
                for (int t = 0; t < predictions[0][0].length; t++) {
                    priceList.add((double) predictions[0][0][t][0]);
                }
            } else if (outputValue instanceof float[]) {
                // 1D 输出
                float[] predictions = (float[]) outputValue;
                priceList = new ArrayList<>();
                for (float price : predictions) {
                    priceList.add((double) price);
                }
            } else {
                throw new IllegalStateException("不支持的输出类型: " + outputValue.getClass().getName());
            }

            // 7. 资源清理
            inputTensor.close();
            result.close();

            log.debug("ONNX 推理完成，预测 {} 个价格点", priceList.size());
            return priceList;

        } catch (OrtException e) {
            log.error("ONNX 推理执行失败", e);
            throw new RuntimeException("电价预测推理失败", e);
        }
    }

    /**
     * 批量预测（支持多个样本）
     * 
     * @param loadProfiles 多个24点负荷曲线
     * @return 每个样本的24点预测电价
     */
    public List<List<Double>> predictBatch(List<List<Double>> loadProfiles) {
        if (session == null || loadProfiles.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            int batchSize = loadProfiles.size();
            float[] inputData = new float[batchSize * inputSize];

            // 展平所有输入
            for (int i = 0; i < batchSize; i++) {
                List<Double> profile = loadProfiles.get(i);
                if (profile.size() != inputSize) {
                    throw new IllegalArgumentException(
                        String.format("样本 %d 维度不匹配: 期望 %d, 实际 %d", 
                            i, inputSize, profile.size()));
                }
                for (int j = 0; j < inputSize; j++) {
                    inputData[i * inputSize + j] = profile.get(j).floatValue();
                }
            }

            // 创建 Tensor
            // 批量推理：[batch_size, 1, seq_len, 1]
            long[] shape = {batchSize, 1, inputSize, 1};
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, 
                FloatBuffer.wrap(inputData), shape);

            // 执行推理
            Map<String, OnnxTensor> inputs = Collections.singletonMap(
                session.getInputNames().iterator().next(), inputTensor);
            
            OrtSession.Result result = session.run(inputs);
            OnnxTensor outputTensor = (OnnxTensor) result.get(0);
            Object outputValue = outputTensor.getValue();

            // 转换输出 - 根据输出类型灵活处理
            List<List<Double>> results = new ArrayList<>();
            
            if (outputValue instanceof float[][]) {
                // 2D 输出: [batch, seq_len]
                float[][] predictions = (float[][]) outputValue;
                for (float[] pred : predictions) {
                    List<Double> priceList = new ArrayList<>();
                    for (float price : pred) {
                        priceList.add((double) price);
                    }
                    results.add(priceList);
                }
            } else if (outputValue instanceof float[][][][]) {
                // 4D 输出: [batch, channel, seq_len, feature]
                float[][][][] predictions = (float[][][][]) outputValue;
                for (int i = 0; i < predictions.length; i++) {
                    List<Double> priceList = new ArrayList<>();
                    // 遍历 [batch][0][time_step][0] 提取所有时间点
                    for (int t = 0; t < predictions[i][0].length; t++) {
                        priceList.add((double) predictions[i][0][t][0]);
                    }
                    results.add(priceList);
                }
            } else {
                throw new IllegalStateException("不支持的批量输出类型: " + outputValue.getClass().getName());
            }

            // 清理资源
            inputTensor.close();
            result.close();

            log.debug("批量 ONNX 推理完成，处理 {} 个样本", batchSize);
            return results;

        } catch (OrtException e) {
            log.error("批量 ONNX 推理失败", e);
            throw new RuntimeException("批量电价预测失败", e);
        }
    }

    /**
     * 获取模型元信息
     */
    public Map<String, Object> getModelMetadata() {
        if (session == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inputNames", session.getInputNames());
        metadata.put("outputNames", session.getOutputNames());
        metadata.put("inputCount", session.getInputNames().size());
        metadata.put("outputCount", session.getOutputNames().size());
        
        try {
            metadata.put("modelMetadata", session.getMetadata());
        } catch (OrtException e) {
            log.warn("无法获取模型元数据", e);
        }

        return metadata;
    }

    /**
     * 销毁：释放资源
     */
    @PreDestroy
    public void destroy() {
        try {
            if (session != null) {
                session.close();
                log.info("ONNX 会话已关闭");
            }
            if (environment != null) {
                environment.close();
                log.info("ONNX 环境已关闭");
            }
        } catch (OrtException e) {
            log.error("关闭 ONNX 资源时出错", e);
        }
    }
}
