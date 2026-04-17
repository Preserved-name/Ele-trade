# AI电力交易辅助系统

基于 Spring AI + LangChain4j 的多Agent电力交易系统

## 项目结构

```
trade/
├── src/main/java/com/jiwuji/
│   ├── agent/                    # Agent层
│   │   ├── DataCollectorTool.java    # 数据采集工具
│   │   ├── PriceForecastTool.java    # 电价预测工具
│   │   ├── RiskTool.java             # 风控工具
│   │   ├── StrategyAgent.java        # 策略Agent
│   │   └── WorkflowOrchestrator.java # 工作流编排器
│   ├── config/                   # 配置层
│   │   └── AiConfig.java             # AI配置
│   ├── controller/               # 控制器层
│   │   └── TradeController.java      # 交易API
│   ├── model/                    # 模型层
│   │   ├── MarketData.java           # 市场数据
│   │   ├── ForecastResult.java       # 预测结果
│   │   ├── Order.java                # 订单
│   │   ├── RiskDecision.java         # 风控决策
│   │   ├── TradeProposal.java        # 交易提案
│   │   ├── Portfolio.java            # 投资组合
│   │   └── TradeResult.java          # 交易结果
│   └── AiTraderApplication.java  # 启动类
├── src/main/resources/
│   └── application.yml           # 应用配置
└── pom.xml                       # Maven配置
```

## 前置要求

1. **Java 17+** - 确保已安装JDK 17或更高版本
2. **Maven 3.6+** - 用于构建项目
3. **Ollama** (可选) - 本地LLM服务，或使用OpenAI API

## 配置说明

### 1. Redis 配置（必需）

编辑 `src/main/resources/application.yml`，填写Redis连接信息：

```yaml
spring:
  data:
    redis:
      host: localhost      # Redis主机地址
      port: 6379           # Redis端口
      password: your_password  # Redis密码（如果有）
```

**快速启动Redis（Docker方式）：**
```bash
docker run -d --name redis -p 6379:6379 redis:latest
```

### 2. AI模型配置（二选一）

#### 方案A：使用Ollama本地模型（推荐用于开发测试）

1. 安装Ollama：https://ollama.com/
2. 拉取模型：
```bash
ollama pull llama3
```
3. 启动Ollama服务（默认监听 http://localhost:11434）
4. 配置保持不变即可

#### 方案B：使用OpenAI API

编辑 `application.yml`：
```yaml
spring.ai:
  openai:
    api-key: sk-your-openai-api-key  # 填写你的OpenAI API密钥
```

同时修改 `AiConfig.java` 中的配置指向OpenAI。

### 3. 其他配置项

- **服务器端口**：默认为8080，可在 `application.yml` 中修改
- **日志级别**：可根据需要调整各模块的日志级别

## 运行项目

### 方式1：使用Maven命令

```bash
# 编译项目
mvn clean package

# 运行应用
mvn spring-boot:run
```

### 方式2：使用IDE

直接在IDE中运行 `AiTraderApplication.java` 的main方法

### 方式3：运行打包后的JAR

```bash
java -jar target/ai-trader-1.0.0-SNAPSHOT.jar
```

## API接口

### 1. 健康检查
```bash
curl http://localhost:8080/trade/health
```

### 2. 快速测试（不使用LLM）
```bash
curl -X POST "http://localhost:8080/trade/test"
```

响应示例：
```json
{
  "sessionId": "test_20260415_143022",
  "success": true,
  "message": "测试订单通过风控",
  "order": {
    "direction": "BUY",
    "price": 0.45,
    "volume": 500,
    "sessionId": "test_20260415_143022"
  }
}
```

### 3. 完整工作流（使用LLM生成策略）
```bash
curl -X POST "http://localhost:8080/trade/start?sessionId=my_session_001"
```

响应示例：
```json
{
  "sessionId": "my_session_001",
  "success": true,
  "message": "订单已成功提交",
  "order": {
    "direction": "BUY",
    "price": 0.4123,
    "volume": 500,
    "sessionId": "my_session_001"
  }
}
```

## 核心功能说明

### 多Agent协作流程

1. **数据采集Agent** (`DataCollectorTool`)
   - 从多源拉取市场数据
   - 清洗和验证数据质量

2. **预测Agent** (`PriceForecastTool`)
   - 基于负荷曲线预测96点电价
   - 输出预测置信度

3. **策略Agent** (`StrategyAgent`)
   - 使用LLM分析预测结果
   - 生成交易策略（买入/卖出）
   - JSON Schema强制输出格式，防止幻觉

4. **风控Agent** (`RiskTool`)
   - 校验订单合规性
   - 多重规则检查（价格、电量、方向等）

5. **工作流编排器** (`WorkflowOrchestrator`)
   - 协调各Agent执行顺序
   - 低置信度降级处理
   - 异常恢复机制

### 降级策略

当预测置信度低于阈值（0.7）时：
- 自动跳过自动交易
- 返回人工决策提示
- 记录日志供后续分析

### 防幻觉机制

1. **JSON Schema约束**：强制LLM输出标准格式
2. **独立风控校验**：硬编码规则作为最后防线
3. **置信度评估**：每个预测附带可信度评分

## 待完善配置清单

以下是需要在生产环境中补充的配置：

### 必需配置
- [ ] Redis连接信息（host、port、password）
- [ ] AI模型服务（Ollama地址或OpenAI API密钥）

### 可选配置（生产环境建议添加）
- [ ] TimescaleDB数据库连接（时序数据存储）
- [ ] MinIO对象存储配置（原始文件存储）
- [ ] Feast特征存储配置
- [ ] RabbitMQ消息队列配置（异步事件）
- [ ] Drools规则引擎配置（高级风控）
- [ ] 交易所网关API配置
- [ ] WebSocket配置（实时推送）
- [ ] 监控告警配置（Prometheus + Grafana）

### 环境变量配置示例

创建 `.env` 文件（或使用K8s ConfigMap）：
```bash
REDIS_HOST=redis.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

OLLAMA_BASE_URL=http://ollama:11434/v1
OLLAMA_MODEL_NAME=llama3

# 或使用OpenAI
OPENAI_API_KEY=sk-your-api-key
OPENAI_BASE_URL=https://api.openai.com/v1
```

## 开发建议

### 扩展真实数据源
在 `DataCollectorTool.fetchMarketData()` 中替换模拟数据为真实API调用：
```java
// 示例：调用电力交易中心API
return webClient.get()
    .uri("https://api.power-exchange.com/market-data/{region}", region)
    .retrieve()
    .bodyToMono(MarketData.class)
    .block();
```

### 集成机器学习模型
在 `PriceForecastTool.forecastPrices()` 中加载ONNX模型：
```java
// 使用onnxruntime进行推理
OrtSession session = env.createSession("model.onnx");
// ... 执行推理
```

### 添加更多风控规则
在 `RiskTool.checkRisk()` 中扩展：
- 账户余额检查
- 持仓限额
- 日内交易次数限制
- 市场流动性检查

## 技术栈

- **框架**: Spring Boot 3.2 + WebFlux
- **AI**: Spring AI 1.0.0-M3 + LangChain4j 0.33.0
- **LLM**: Ollama (Llama3) / OpenAI GPT
- **缓存**: Redis (Reactive)
- **构建**: Maven
- **语言**: Java 17

## 注意事项

1. **首次运行**可能需要下载Maven依赖，请耐心等待
2. **Ollama模型**首次拉取可能需要较长时间（约4GB）
3. **生产环境**建议使用容器化部署（Docker + K8s）
4. **安全性**：不要将API密钥提交到版本控制系统

## 下一步优化方向

1. 集成TimescaleDB存储历史数据
2. 实现真正的机器学习模型推理
3. 添加WebSocket实时推送
4. 实现交易员前端界面
5. 接入真实交易所API
6. 添加单元测试和集成测试
7. 实现分布式锁和限流
8. 添加完整的监控和告警

## 联系与支持

如有问题，请查看日志输出或检查配置文件是否正确。
