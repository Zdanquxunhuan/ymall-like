# YMall-like Platform

## 快速开始

```bash
docker compose up -d
```

```bash
mvn -q -pl demo-service -am spring-boot:run
```

> 需要 JDK17 + Maven。

## 核心能力

- 统一返回体 `Result<T>` + `traceId`
- 统一错误码 + 业务异常 + 全局异常处理
- traceId 生成与透传（Filter + MDC + 响应头）
- MyBatis-Plus 全局配置（乐观锁/逻辑删除/自动填充/分页）
- 幂等组件（Redis 实现）
- 限流组件（Redis Lua 令牌桶）
- RocketMQ 模板 + Outbox 中继骨架

## curl 示例

### 幂等接口

```bash
curl -X POST http://localhost:8080/demo/idempotent \
  -H 'idempotency_key: demo-1'
```

重复请求会返回相同结果。

### 限流接口

```bash
curl http://localhost:8080/demo/ratelimit -H 'X-User-Id: u1'
```

触发限流时返回 `RATE-429` 错误码。

## k6 压测

```bash
k6 run k6/idempotent.js
k6 run k6/ratelimit.js
```

## 常见故障排查

1. **Redis 连接失败**：确认 `docker compose ps` 中 redis 端口 6379 已启动。
2. **RocketMQ 连接失败**：确认 namesrv 9876 与 broker 10911 端口已开放。
3. **MySQL 连接失败**：确认 root/root 用户可连接，数据库 `ymall_demo` 已创建。
4. **traceId 缺失**：检查 `TraceIdFilter` 是否被扫描到（`scanBasePackages` 包含 `com.ymall.platform`）。
