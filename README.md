# AI电力交易辅助系统

基于 Spring AI + LangChain4j 的多Agent电力交易系统，支持智能电价预测、策略生成、审计校验和风险控制。

## ✨ 核心特性

- 🤖 **多Agent协作**：数据采集、预测、策略、审计、风控、执行6大智能体协同工作
- 📊 **实时可视化**：内置Web界面，实时展示工作流进度和交易结果
- 🛡️ **防幻觉机制**：JSON Schema约束 + 独立审计Agent + 硬编码风控规则三重保障
- 🔄 **降级策略**：低置信度自动降级，确保系统稳定性
- ⚡ **响应式架构**：基于Spring WebFlux的异步非阻塞设计
- 🔍 **完整追溯**：会话级别的状态追踪和历史记录
- 📈 **ONNX模型推理**：集成LightGBM电价预测模型，毫秒级推理性能
- 📝 **Excel数据读取**：支持历史电价数据导入和分析

## 项目结构

```
trade/
├── src/main/java/com/jiwuji/
│   ├── agent/                    # Agent层
│   │   ├── DataCollectorTool.java    # 数据采集工具（Excel数据读取）
│   │   ├── ExcelDataReader.java      # Excel数据读取器（新增）
│   │   ├── PriceForecastTool.java    # 电价预测工具（ONNX模型）
│   │   ├── OnnxModelEngine.java      # ONNX模型引擎（新增）
│   │   ├── StrategyAgent.java        # 策略Agent
│   │   ├── AuditAgent.java           # 审计Agent
│   │   ├── RiskTool.java             # 风控工具
│   │   └── WorkflowOrchestrator.java # 工作流编排器
│   ├── config/                   # 配置层
│   │   ├── AiConfig.java             # AI配置
│   │   ├── CorsConfig.java           # 跨域配置
│   │   └── RedisConfig.java          # Redis配置
│   ├── controller/               # 控制器层
│   │   ├── TradeController.java      # 交易API
│   │   └── WorkflowController.java   # 工作流API
│   ├── model/                    # 模型层
│   │   ├── MarketData.java           # 市场数据
│   │   ├── ForecastResult.java       # 预测结果
│   │   ├── Order.java                # 订单
│   │   ├── RiskDecision.java         # 风控决策
│   │   ├── TradeProposal.java        # 交易提案
│   │   ├── Portfolio.java            # 投资组合
│   │   ├── TradeResult.java          # 交易结果
│   │   └── WorkflowState.java        # 工作流状态
│   └── AiTraderApplication.java  # 启动类
├── src/main/resources/
│   ├── data/
│   │   └── history_price.xlsx        # 历史电价数据
│   ├── models/
│   │   └── adv_inception_v3_Opset16.onnx  # ONNX预测模型
│   └── application.yml           # 应用配置
├── models/                       # 模型训练脚本
│   └── train_and_export.py       # LightGBM训练与导出
├── frontend/                     # 前端界面
│   └── index.html                # Vue3单页应用
├── start.ps1                     # Windows快速启动脚本
├── deploy-model.sh               # Linux/Mac模型部署脚本
├── deploy-model.ps1              # Windows模型部署脚本
├── docker-compose.yml            # Docker编排配置
├── Dockerfile                    # Docker镜像构建
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

### 2. AI模型配置（三选一）

#### 方案A：使用DeepSeek API（推荐）

编辑 `application.yml` 或设置环境变量：
```yaml
spring.ai:
  openai:
    api-key: ${DEEPSEEK_API_KEY:your-deepseek-api-key}
    base-url: https://api.deepseek.com
    chat:
      options:
        model: deepseek-chat
        temperature: 0.1
```

**环境变量方式（推荐）：**
```bash
# PowerShell
$env:DEEPSEEK_API_KEY="sk-your-api-key"

# Linux/Mac
export DEEPSEEK_API_KEY="sk-your-api-key"
```

获取API密钥：访问 [DeepSeek官网](https://platform.deepseek.com/) 注册并创建API Key。

#### 方案B：使用通义千问（阿里云DashScope）

编辑 `application.yml`：
```yaml
ai:
  dashscope:
    api-key: ${ALI_API_KEY:your-dashscope-api-key}
    model-name: qwen-plus
```

需要在代码中切换使用DashScope模型。

获取API密钥：访问 [阿里云DashScope](https://dashscope.aliyun.com/) 开通服务。

#### 方案C：使用Ollama本地模型（开发测试）

1. 安装Ollama：https://ollama.com/
2. 拉取模型：
```bash
ollama pull llama3
```
3. 启动Ollama服务（默认监听 http://localhost:11434）
4. 修改配置指向本地Ollama服务

> 💡 **提示**：生产环境推荐使用DeepSeek或通义千问API，本地模型仅用于开发测试。

### 3. ONNX模型配置（可选）

系统已集成LightGBM电价预测模型，通过ONNX Runtime实现高性能推理。

**启用ONNX模型：**
```yaml
model:
  enabled: true                    # 启用ONNX模型推理
  fallback:
    enabled: true                  # 失败时降级到模拟模式
  path: classpath:models/adv_inception_v3_Opset16.onnx
  input:
    size: 96                       # 输入维度（96个时间点）
```

**快速部署模型：**
```bash
# Windows
.\deploy-model.ps1

# Linux/Mac
chmod +x deploy-model.sh
./deploy-model.sh
```

详细文档请参考：[MODEL_DEPLOYMENT_GUIDE.md](MODEL_DEPLOYMENT_GUIDE.md)

### 4. Excel数据配置

系统支持从Excel文件读取历史电价数据：
```yaml
excel:
  data:
    path: classpath:data/history_price.xlsx  # 数据文件路径
    time-slot-column: 1                       # 时间槽列索引
    avg-price-column: 9                       # 平均价格列索引
```

将您的历史数据放置在 `src/main/resources/data/` 目录下即可。

## 运行项目

### 方式1：使用PowerShell快速启动（推荐Windows用户）

```powershell
.\start.ps1
```

该脚本会自动检查环境并启动应用。

### 方式2：使用Maven命令

```bash
# 编译项目
mvn clean package

# 运行应用
mvn spring-boot:run
```

### 方式3：使用IDE

直接在IDE中运行 `AiTraderApplication.java` 的main方法

### 方式4：运行打包后的JAR

```bash
java -jar target/ai-trader-1.0.0-SNAPSHOT.jar
```

### 访问前端界面

启动成功后，在浏览器中打开：
```
frontend/index.html
```

或直接拖拽文件到浏览器中打开。

## API接口

### 1. 健康检查
```bash
curl http://localhost:8080/trade/health
```

响应：
```json
{
  "status": "UP",
  "timestamp": "2026-04-17T10:30:00"
}
```

### 2. 快速测试（不使用LLM）
```bash
curl -X POST "http://localhost:8080/trade/test"
```

响应示例：
```json
{
  "sessionId": "test_20260417_103000",
  "success": true,
  "message": "测试订单通过风控",
  "order": {
    "direction": "BUY",
    "price": 0.45,
    "volume": 500,
    "sessionId": "test_20260417_103000"
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

### 4. 审计拦截测试（演示审计功能）
```bash
curl -X POST "http://localhost:8080/trade/start-audit-test?sessionId=audit_test_001"
```

此接口会触发审计Agent的拦截逻辑，用于测试防幻觉机制。

### 5. 查询工作流状态
```bash
curl "http://localhost:8080/workflow/status?sessionId=my_session_001"
```

响应示例：
```json
{
  "sessionId": "my_session_001",
  "currentStep": "EXECUTED",
  "data": {
    "lastPrice": 0.45,
    "confidence": 0.85,
    "strategy": {
      "order": {
        "direction": "BUY",
        "price": 0.4123,
        "volume": 500
      }
    },
    "auditPassed": true
  }
}
```

## 🆕 ONNX Runtime (LightGBM) 模型推理

系统现已支持 **ONNX Runtime** 进行高性能电价预测，提供毫秒级推理能力。

### 快速开始

**方式 1: 自动部署（推荐）**

```bash
# Linux/Mac
chmod +x deploy-model.sh
./deploy-model.sh

# Windows PowerShell
.\deploy-model.ps1
```

**方式 2: 手动部署**

```bash
# 1. 安装 Python 依赖
cd models
pip install lightgbm onnxmltools onnxruntime numpy pandas scikit-learn

# 2. 训练并导出模型
python train_and_export.py

# 3. 编译 Java 项目
cd ..
mvn clean package

# 4. 启动应用
mvn spring-boot:run
```

**方式 3: Docker 部署**

```bash
# 构建并启动
docker-compose up -d

# 查看日志
docker-compose logs -f ai-trader
```

### 配置说明

在 `application.yml` 中配置：

```yaml
model:
  enabled: true                    # 启用 ONNX 模型
  fallback:
    enabled: true                  # 失败时降级到模拟模式
  path: classpath:models/price_forecast.onnx
  input:
    size: 96                       # 输入维度
```

### 性能指标

| 场景 | 延迟 (P50) | 吞吐量 (QPS) |
|------|-----------|-------------|
| 单次推理 | 3-5ms | 200+ |
| 批量推理 (batch=10) | 15-20ms | 500+ |
| 带缓存 | <1ms | 1000+ |

详细部署文档请参考：[MODEL_DEPLOYMENT_GUIDE.md](MODEL_DEPLOYMENT_GUIDE.md)

---

## 📊 Excel数据读取

系统支持从Excel文件读取历史电价数据，用于模型训练和回测。

### 数据格式要求

- **文件格式**：`.xlsx` 或 `.xls`
- **时间槽列**：第1列（索引从0开始），包含96个时间点（00:15, 00:30, ..., 24:00）
- **价格列**：第9列，包含对应时间点的平均电价

### 示例数据结构

| 时间槽 | ... | 平均电价 |
|--------|-----|----------|
| 00:15  | ... | 0.45     |
| 00:30  | ... | 0.43     |
| ...    | ... | ...      |
| 24:00  | ... | 0.48     |

### 使用示例

```java
// 在DataCollectorTool中自动读取
MarketData data = excelDataReader.readHistoricalData();
```

---

## 核心功能说明

### 多Agent协作流程

```
┌─────────────┐
│  用户请求   │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ 1. 数据采集Agent │ ← 从多源拉取市场数据，清洗验证
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ 2. 预测Agent     │ ← 基于负荷曲线预测96点电价，输出置信度
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ 3. 策略Agent     │ ← LLM分析预测结果，生成交易策略（JSON Schema约束）
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│ 4. 审计Agent     │ ← 防幻觉审计，校验策略合理性（新增）
└──────┬──────────┘
       │
       ├─ 审计不通过 → 拦截并返回警告
       │
       ▼ 审计通过
┌─────────────────┐
│ 5. 风控Agent     │ ← 多重规则检查（价格、电量、方向等）
└──────┬──────────┘
       │
       ├─ 风控不通过 → 拒绝订单
       │
       ▼ 风控通过
┌─────────────────┐
│ 6. 订单执行      │ ← 提交订单，返回结果
└─────────────────┘
```

### Agent详细说明

1. **数据采集Agent** (`DataCollectorTool` + `ExcelDataReader`)
   - 从Excel文件读取历史电价数据
   - 支持多源数据整合
   - 数据清洗和验证
   - 可扩展为真实API调用

2. **预测Agent** (`PriceForecastTool` + `OnnxModelEngine`)
   - 基于历史数据和负荷曲线预测96点电价
   - 集成LightGBM模型（ONNX格式）
   - 输出预测置信度（0-1之间）
   - 毫秒级推理性能，支持批量预测

3. **策略Agent** (`StrategyAgent`)
   - 使用LLM分析预测结果和市场趋势
   - 生成交易策略（买入/卖出/观望）
   - JSON Schema强制输出格式，防止幻觉
   - 支持分批建仓策略优化仓位管理

4. **审计Agent** (`AuditAgent`)
   - 独立于策略生成的第二道防线
   - 检测异常交易行为（价格偏离、数量异常等）
   - 可拦截高风险策略，防止LLM幻觉
   - 提供审计日志供后续分析和追溯

5. **风控Agent** (`RiskTool`)
   - 硬编码规则作为最后防线
   - 校验订单合规性
   - 多重规则检查：
     - 价格范围检查
     - 电量限额检查
     - 交易方向校验
     - 账户余额检查（待实现）

6. **工作流编排器** (`WorkflowOrchestrator`)
   - 协调各Agent按顺序执行
   - 低置信度自动降级处理
   - 异常恢复和容错机制
   - 会话状态管理（Redis存储）
   - 完整的执行链路追踪

### 防幻觉机制（三层防护）

#### 第一层：JSON Schema约束
- 强制LLM输出标准JSON格式
- 字段类型和范围严格校验
- 避免自由文本导致的格式错误

#### 第二层：审计Agent独立校验 ⭐ 新增
- 独立的审计Agent对策略进行二次审核
- 检测价格偏离度、交易量异常等风险
- 可主动拦截可疑策略
- 提供详细的审计日志

#### 第三层：硬编码风控规则
- 基于业务规则的最终防线
- 不依赖LLM，100%可靠
- 包括价格上下限、电量限制等

### 降级策略

当预测置信度低于阈值（0.7）时：
- 自动跳过自动交易
- 返回人工决策提示
- 记录日志供后续分析
- 建议交易员手动介入

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

## 环境变量配置

推荐使用环境变量管理敏感配置：

### PowerShell (Windows)
```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD="your_redis_password"
$env:DEEPSEEK_API_KEY="sk-your-deepseek-api-key"
$env:DASHSCOPE_MODEL_NAME="qwen-plus"
```

### Linux/Mac
```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password
export DEEPSEEK_API_KEY=sk-your-deepseek-api-key
export DASHSCOPE_MODEL_NAME=qwen-plus
```

### Docker/K8s ConfigMap
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ai-trader-config
data:
  REDIS_HOST: "redis-service"
  REDIS_PORT: "6379"
  DEEPSEEK_BASE_URL: "https://api.deepseek.com"
  DASHSCOPE_MODEL_NAME: "qwen-plus"
---
apiVersion: v1
kind: Secret
metadata:
  name: ai-trader-secrets
type: Opaque
data:
  REDIS_PASSWORD: <base64-encoded>
  DEEPSEEK_API_KEY: <base64-encoded>
  ALI_API_KEY: <base64-encoded>
```

## 前端界面

系统内置了基于Vue3的Web界面，提供：

- 🎮 **控制面板**：一键启动工作流、快速测试、审计测试
- 📊 **实时进度**：6步工作流可视化展示
- 📋 **交易结果**：详细的订单信息和成功/失败状态
- 📜 **历史记录**：查看过往交易会话
- 🔍 **状态查询**：实时查询任意会话的工作流状态

直接打开 `frontend/index.html` 即可使用。

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

### 后端框架
- **核心框架**: Spring Boot 3.2.0 + WebFlux（响应式编程）
- **AI集成**: Spring AI 1.0.0-M6 + LangChain4j 0.35.0
- **LLM服务**: DeepSeek / 通义千问 / Ollama (Llama3)
- **缓存存储**: Redis (Reactive)
- **数据解析**: Apache POI 5.2.5 (Excel处理)
- **模型推理**: ONNX Runtime 1.16.3
- **构建工具**: Maven 3.6+
- **开发语言**: Java 17
- **辅助工具**: Lombok, Jackson

### 前端技术
- **框架**: Vue 3 (CDN方式)
- **UI库**: Element Plus
- **HTTP客户端**: Axios
- **样式**: Tailwind CSS

### DevOps
- **容器化**: Docker + Docker Compose
- **监控**: Spring Boot Actuator (health, metrics)
- **日志**: SLF4J + Logback

## 常见问题

### 1. Redis连接失败
**问题**：启动时报Redis连接错误
**解决**：
```bash
# 使用Docker快速启动Redis
docker run -d --name redis -p 6379:6379 redis:latest

# 或者检查application.yml中的Redis配置是否正确
```

### 2. API密钥未配置
**问题**：调用LLM时报错缺少API密钥
**解决**：设置环境变量
```powershell
# PowerShell
$env:DEEPSEEK_API_KEY="sk-your-api-key"

# 或在application.yml中直接配置
```

### 3. 跨域问题
**问题**：前端无法访问后端API
**解决**：已配置CorsConfig，确保前端和后端在同一域名或正确配置CORS

### 4. 模型响应慢
**问题**：策略生成耗时较长
**解决**：
- 检查网络连接（API服务）
- 降低temperature参数提高响应速度
- 考虑使用本地Ollama模型
- 启用ONNX模型加速预测环节

### 5. ONNX模型加载失败
**问题**：启动时报ONNX模型加载错误
**解决**：
```bash
# 确认模型文件存在
ls src/main/resources/models/

# 禁用ONNX模型，使用模拟模式
# 在application.yml中设置 model.enabled: false
```

### 6. Excel数据读取失败
**问题**：无法读取历史电价数据
**解决**：
- 检查文件路径是否正确：`src/main/resources/data/history_price.xlsx`
- 确认Excel格式符合要求（.xlsx或.xls）
- 验证列索引配置是否匹配实际数据结构

### 7. 审计拦截测试无反应
**问题**：调用审计测试接口未触发拦截
**解决**：检查AuditAgent中的测试逻辑，确认触发了异常策略

## 下一步优化方向

### 已完成功能
- ✅ 多Agent协作架构
- ✅ 审计Agent防幻觉机制
- ✅ Web前端界面
- ✅ ONNX模型推理集成
- ✅ Excel数据读取功能
- ✅ 分批建仓策略优化
- ✅ Docker容器化部署

### 短期优化
- [ ] 集成TimescaleDB存储历史时序数据
- [ ] 添加WebSocket实时推送交易状态
- [ ] 完善单元测试和集成测试（覆盖率>80%）
- [ ] 实现模型版本管理和A/B测试
- [ ] 添加更多数据源接口（天气、负荷预测等）

### 中期优化
- [ ] 接入真实电力交易所API
- [ ] 实现分布式锁和限流机制
- [ ] 添加完整的监控告警（Prometheus + Grafana）
- [ ] 实现专业交易员前端界面（K线图、深度图等）
- [ ] 扩展风控规则（持仓限额、日内交易次数、VaR等）
- [ ] 支持多区域电力市场

### 长期优化
- [ ] 微服务架构拆分（Agent独立部署）
- [ ] 强化学习优化交易策略
- [ ] 区块链交易记录存证
- [ ] 移动端App开发（iOS/Android）
- [ ] 智能合约自动执行交易

## 联系与支持

如有问题，请查看日志输出或检查配置文件是否正确。

### 相关文档
- [快速开始指南](QUICKSTART.md)
- [模型部署指南](MODEL_DEPLOYMENT_GUIDE.md)
- [ONNX快速入门](ONNX_QUICKSTART.md)
- [配置指南](CONFIG_GUIDE.md)
- [核心代码演示](CORE_CODE_DEMO.md)
- [项目结构说明](PROJECT_STRUCTURE.md)
- [系统设计方案](AI电力交易辅助系统设计方案-宗杰.md)

### 许可证
本项目仅供学习和研究使用。

---

**最后更新**: 2026-04-17
