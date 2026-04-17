# AI电力交易辅助系统

> 基于 Spring AI + LangChain4j 的多Agent智能电力交易系统，实现电价预测、策略生成、审计校验和风险控制的全自动化交易流程。

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 🌟 核心特性

- 🤖 **多Agent协作** - 数据采集、预测、策略、审计、风控、执行6大智能体协同工作
- 📊 **实时可视化** - 内置Vue3 Web界面，实时展示工作流进度和交易结果
- 🛡️ **三层防幻觉** - JSON Schema约束 + 独立审计Agent + 硬编码风控规则
- ⚡ **大模型预测** - 基于LLM的智能电价预测，灵活准确
- 🔄 **智能降级** - 低置信度自动降级，确保系统稳定性
- 📈 **完整追溯** - Redis会话管理，支持全链路状态追踪
- 📝 **Excel数据导入** - 支持历史电价数据读取和分析

## 🚀 快速开始

### 前置要求

- Java 17+
- Maven 3.6+
- Redis (可选，用于会话存储)
- Ollama 或 API密钥 (DeepSeek/通义千问)

### 5分钟启动

#### 1. 克隆项目
```bash
git clone <repository-url>
cd trade
```

#### 2. 配置Redis（可选）
```bash
# Docker方式快速启动Redis
docker run -d --name redis -p 6379:6379 redis:latest
```

#### 3. 配置AI服务（三选一）

**方案A：DeepSeek API（推荐）**
```bash
# 设置环境变量
export DEEPSEEK_API_KEY="sk-your-api-key"
```

**方案B：通义千问**
```bash
export ALI_API_KEY="your-dashscope-api-key"
```

**方案C：Ollama本地模型**
```bash
ollama pull llama3
# Ollama默认监听 http://localhost:11434
```

#### 4. 启动应用
```bash
# Windows PowerShell
.\start.ps1

# 或使用Maven
mvn spring-boot:run
```

#### 5. 访问前端界面
打开浏览器访问：`frontend/index.html`

---

## 📖 详细文档

| 文档 | 说明 |
|------|------|
| [配置指南](CONFIG_GUIDE.md) | 完整的配置项说明 |
| [核心代码演示](CORE_CODE_DEMO.md) | 关键代码示例 |
| [系统设计方案](AI电力交易辅助系统设计方案-宗杰.md) | 架构设计和实现细节 |

## 🏗️ 系统架构

### 多Agent协作流程

```
用户请求 → 数据采集 → 价格预测 → 策略生成 → 审计校验 → 风险控制 → 订单执行
           (Agent 1)   (Agent 2)   (Agent 3)   (Agent 4)   (Agent 5)   (Agent 6)
```

### Agent职责说明

| Agent | 职责 | 关键技术 |
|-------|------|----------|
| **数据采集** | 从Excel/API获取市场数据 | Apache POI, WebClient |
| **价格预测** | 96点电价预测+置信度评估 | Spring AI, LLM |
| **策略生成** | LLM分析生成交易策略 | Spring AI, JSON Schema |
| **审计校验** | 防幻觉二次审核 | 独立Agent校验 |
| **风险控制** | 硬编码规则检查 | 业务规则引擎 |
| **工作流编排** | 协调各Agent执行 | Reactor, Redis |

### 三层防幻觉机制

1. **JSON Schema约束** - 强制LLM输出标准格式
2. **审计Agent校验** - 独立第二道防线检测异常
3. **硬编码风控规则** - 最终可靠防线

---

## 📡 API接口

### 基础接口

```bash
# 健康检查
curl http://localhost:8080/trade/health

# 快速测试（不使用LLM）
curl -X POST http://localhost:8080/trade/test

# 完整工作流（使用LLM）
curl -X POST "http://localhost:8080/trade/start?sessionId=session_001"

# 查询工作流状态
curl "http://localhost:8080/workflow/status?sessionId=session_001"
```

### 响应示例

```json
{
  "sessionId": "session_001",
  "success": true,
  "message": "订单已成功提交",
  "order": {
    "direction": "BUY",
    "price": 0.4123,
    "volume": 500
  }
}
```

## ⚙️ 配置说明

### 核心配置项

编辑 `src/main/resources/application.yml`：

```yaml
# Redis配置（可选）
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}

# AI服务配置
spring.ai:
  openai:
    api-key: ${DEEPSEEK_API_KEY:your-api-key}
    base-url: https://api.deepseek.com
    chat:
      options:
        model: deepseek-chat
        temperature: 0.1

# Excel数据配置
excel:
  data:
    path: classpath:data/history_price.xlsx
    time-slot-column: 1
    avg-price-column: 9
```

### 环境变量（推荐）

```bash
export DEEPSEEK_API_KEY="sk-your-api-key"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD="your-password"
```

## 🧪 技术栈

### 后端
- **框架**: Spring Boot 3.2 + WebFlux
- **AI**: Spring AI 1.0-M6 + LangChain4j 0.35
- **LLM**: DeepSeek / 通义千问 / Ollama
- **缓存**: Redis (Reactive)
- **数据处理**: Apache POI 5.2.5
- **语言**: Java 17

### 前端
- **框架**: Vue 3
- **UI**: Element Plus
- **HTTP**: Axios
- **样式**: Tailwind CSS

### DevOps
- **容器**: Docker + Docker Compose
- **监控**: Spring Boot Actuator
- **日志**: SLF4J + Logback

## 🐛 常见问题

### Redis连接失败
```bash
# 启动Redis容器
docker run -d --name redis -p 6379:6379 redis:latest
```

### API密钥未配置
```bash
# 设置环境变量
export DEEPSEEK_API_KEY="sk-your-api-key"
```

### 模型响应慢
- 检查网络连接
- 降低temperature参数
- 使用本地Ollama模型

## 📋 待完善功能

### 短期优化
- [ ] TimescaleDB时序数据存储
- [ ] WebSocket实时推送
- [ ] 单元测试覆盖率 >80%
- [ ] 模型版本管理和A/B测试
- [ ] 更多数据源接口

### 中期优化
- [ ] 真实电力交易所API接入
- [ ] 分布式锁和限流
- [ ] Prometheus + Grafana监控
- [ ] 专业交易员前端界面
- [ ] 扩展风控规则（VaR等）

### 长期规划
- [ ] 微服务架构拆分
- [ ] 强化学习优化策略
- [ ] 区块链交易存证
- [ ] 移动端App
- [ ] 智能合约自动执行

## 📄 许可证

本项目仅供学习和研究使用。

---

**最后更新**: 2026-04-17 | **版本**: 1.0.0
