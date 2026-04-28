# Network Monitor System

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2021.0.8-blue.svg)](https://spring.io/projects/spring-cloud)
[![MySQL](https://img.shields.io/badge/MySQL-8.0+-blue.svg)](https://www.mysql.com/)

一个基于 Spring Cloud 微服务架构的网络流量监控与安全防御系统，提供实时流量分析、攻击检测、自动防御、漏洞监测等功能。

## 目录

- [项目简介](#项目简介)
- [系统架构](#系统架构)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [核心模块](#核心模块)
- [API 文档](#api-文档)
- [数据库设计](#数据库设计)
- [配置说明](#配置说明)

## 项目简介

Network Monitor System 是一个企业级网络安全监控平台，采用微服务架构设计，实现了从流量采集、攻击检测到防御执行的完整安全防护链路。系统支持 SQL 注入、XSS、命令注入、路径遍历、DDoS 等多种攻击类型的检测与防御。

### 核心能力

- **实时流量监控**：全量 HTTP 流量采集与分析
- **智能攻击检测**：基于规则引擎的多阶段攻击检测
- **自动防御响应**：IP 黑名单、请求限流、恶意请求拦截
- **漏洞生命周期管理**：漏洞发现、验证、修复跟踪
- **安全态势感知**：可视化仪表盘、攻击趋势分析

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              客户端请求                                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Gateway Service (Port: 9000)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ IP黑名单过滤 │  │ 请求限流    │  │ 攻击规则检测 │  │ 流量采集     │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
│                              │                                               │
│                    ┌─────────┴─────────┐                                    │
│                    ▼                   ▼                                    │
│            推送流量/攻击事件      下发防御指令                                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                   ▼
┌───────────────────────────────┐    ┌───────────────────────────────────────┐
│   Target Service (Port: 9001) │    │       Monitor Service (Port: 9002)     │
│                               │    │                                        │
│  ┌─────────────────────────┐  │    │  ┌─────────────┐  ┌─────────────┐     │
│  │ 漏洞靶场接口             │  │    │  │ 规则引擎     │  │ 防御决策     │     │
│  │ - SQL 注入              │  │    │  └─────────────┘  └─────────────┘     │
│  │ - XSS                   │  │    │  ┌─────────────┐  ┌─────────────┐     │
│  │ - 命令注入              │  │    │  │ 告警管理     │  │ 漏洞监测     │     │
│  │ - 路径遍历              │  │    │  └─────────────┘  └─────────────┘     │
│  │ - SSRF                  │  │    │  ┌─────────────┐  ┌─────────────┐     │
│  │ - XXE                   │  │    │  │ 用户管理     │  │ 数据报表     │     │
│  │ - 文件包含              │  │    │  └─────────────┘  └─────────────┘     │
│  │ - 反序列化              │  │    │                                        │
│  │ - CSRF                  │  │    └───────────────────────────────────────┘
│  │ - DDoS                  │  │                      │
│  └─────────────────────────┘  │                      ▼
│                               │              ┌───────────────┐
└───────────────────────────────┘              │    MySQL      │
                                               │  (Port: 3306) │
                                               └───────────────┘
```

## 功能特性

### 流量监测

- 全量 HTTP 流量采集与存储
- 流量聚合统计（按 IP、URI、时间维度）
- 流量趋势分析与可视化
- 异常流量自动标记

### 攻击检测

| 攻击类型 | 检测方式 | 风险等级 |
|---------|---------|---------|
| SQL 注入 | 正则匹配 + 语义分析 | HIGH |
| XSS 攻击 | 标签/事件/协议检测 | MEDIUM/HIGH |
| 命令注入 | 系统命令特征匹配 | HIGH |
| 路径遍历 | 路径特征检测 | HIGH |
| DDoS 攻击 | 滑动窗口 + IP 状态机 | HIGH |
| 文件包含 | 协议/函数检测 | CRITICAL |
| SSRF | 内网地址检测 | HIGH |

### 防御机制

- **IP 黑名单**：自动/手动封禁恶意 IP
- **请求限流**：基于滑动窗口的频率控制
- **恶意请求拦截**：实时阻断攻击请求
- **置信度评估**：多维度攻击置信度计算

### 管理功能

- **仪表盘**：安全态势总览、攻击趋势图表
- **规则管理**：攻击检测规则的增删改查
- **白名单管理**：路径、IP、请求头白名单
- **告警管理**：告警生成、确认、忽略
- **漏洞监测**：漏洞发现、验证状态跟踪
- **溯源查询**：攻击链路追踪与分析
- **用户管理**：用户、角色、权限管理
- **操作日志**：用户操作审计

## 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|-----|------|-----|
| Java | 17 | 开发语言 |
| Spring Boot | 2.7.18 | 基础框架 |
| Spring Cloud Gateway | 2021.0.8 | 响应式网关 |
| Spring MVC | 5.x | Web 框架 |
| MyBatis | 2.3.1 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Spring Security Crypto | - | 密码加密 |
| JWT (jjwt) | 0.11.5 | Token 认证 |
| Spring Mail | - | 邮件发送 |
| Lombok | 1.18.30 | 代码简化 |

### 前端技术

| 技术 | 说明 |
|-----|-----|
| Thymeleaf | 服务端模板引擎 |
| Bootstrap | UI 框架 |
| jQuery | JavaScript 库 |
| ECharts | 数据可视化 |
| DataTables | 数据表格 |

## 项目结构

```
network-monitor-parent/
├── gateway-service/                 # 网关服务 (Port: 9000)
│   ├── src/main/java/com/network/gateway/
│   │   ├── cache/                   # 缓存层（规则、黑名单、限流）
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # 控制器
│   │   ├── defense/                 # 防御逻辑
│   │   ├── filter/                  # Gateway 过滤器
│   │   │   ├── collect/             # 流量采集过滤器
│   │   │   └── defense/             # 防御过滤器
│   │   ├── service/                 # 业务服务
│   │   ├── task/                    # 定时任务
│   │   └── traffic/                 # 流量处理
│   └── src/main/resources/
│       └── application.yml          # 配置文件
│
├── monitor-service/                 # 监测服务 (Port: 9002)
│   ├── src/main/java/com/network/monitor/
│   │   ├── cache/                   # 本地缓存
│   │   ├── client/                  # 外部服务客户端
│   │   ├── common/                  # 公共组件
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # 控制器
│   │   │   ├── inner/               # 内部接口（网关调用）
│   │   │   └── outer/               # 外部接口（前端调用）
│   │   ├── entity/                  # 实体类
│   │   ├── mapper/                  # MyBatis Mapper
│   │   └── service/                 # 业务服务
│   └── src/main/resources/
│       ├── templates/               # Thymeleaf 模板
│       └── application.yml          # 配置文件
│
├── target-service/                  # 靶场服务 (Port: 9001)
│   ├── src/main/java/com/network/target/
│   │   ├── controller/              # 漏洞演示控制器
│   │   └── entity/                  # 实体类
│   └── src/main/resources/
│       └── templates/               # 漏洞演示页面
│
├── database/                        # 数据库脚本
│   ├── init_network_monitor.sql     # 主数据库初始化
│   └── init_vuln_target.sql         # 靶场数据库初始化
│
└── pom.xml                          # 父工程 POM
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- 8GB+ 内存推荐

### 安装步骤

**1. 克隆项目**

```bash
git clone https://github.com/your-username/network-monitor-parent.git
cd network-monitor-parent
```

**2. 初始化数据库**

```bash
# 登录 MySQL
mysql -u root -p

# 执行初始化脚本
source database/init_network_monitor.sql
source database/init_vuln_target.sql
```

**3. 修改配置**

修改各服务的 `application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/network_monitor?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

**4. 编译打包**

```bash
mvn clean package -DskipTests
```

**5. 启动服务**

```bash
# 启动网关服务
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar

# 启动监测服务
java -jar monitor-service/target/monitor-service-1.0.0-SNAPSHOT.jar

# 启动靶场服务
java -jar target-service/target/target-service-1.0.0-SNAPSHOT.jar
```

**6. 访问系统**

- 管理后台：http://localhost:9002/
- 默认账号：admin / admin123

## 核心模块

### 网关服务

网关服务是系统的统一流量入口，主要职责：

- **流量采集**：采集所有 HTTP 请求信息
- **实时防御**：IP 黑名单、请求限流、恶意请求拦截
- **攻击检测**：两阶段规则匹配（URL/Query/Header + Body）
- **状态管理**：IP 攻击状态机（NORMAL → SUSPICIOUS → ATTACKING → DEFENDED → COOLDOWN）

### 监测服务

监测服务是系统的业务中枢，主要职责：

- **流量分析**：接收并分析网关推送的流量数据
- **规则引擎**：二次规则匹配，补充检测
- **防御决策**：根据攻击类型生成防御指令
- **告警管理**：攻击告警生成与处理
- **漏洞监测**：漏洞验证与状态跟踪

### 靶场服务

靶场服务提供漏洞演示环境，包含：

- SQL 注入漏洞演示
- XSS 漏洞演示
- 命令注入漏洞演示
- 路径遍历漏洞演示
- SSRF 漏洞演示
- XXE 漏洞演示
- 文件包含漏洞演示
- 反序列化漏洞演示
- CSRF 漏洞演示
- DDoS 攻击目标

## API 文档

### 内部接口（网关调用）

| 接口 | 方法 | 说明 |
|-----|------|-----|
| `/api/inner/traffic/receive` | POST | 接收流量数据 |
| `/api/inner/attack/event` | POST | 接收攻击事件 |
| `/api/inner/gateway/rules` | GET | 获取攻击规则 |
| `/api/inner/gateway/whitelists` | GET | 获取白名单 |
| `/api/inner/gateway/blacklist/sync` | POST | 同步黑名单 |

### 外部接口（前端调用）

| 模块 | 接口前缀 | 说明 |
|-----|---------|-----|
| 认证 | `/api/auth` | 登录、登出、用户信息 |
| 仪表盘 | `/api/dashboard` | 统计数据、趋势图表 |
| 流量管理 | `/api/traffic` | 流量查询、详情 |
| 攻击事件 | `/api/attack-event` | 攻击事件管理 |
| 规则管理 | `/api/rule` | 攻击规则管理 |
| 白名单 | `/api/whitelist` | 白名单管理 |
| 黑名单 | `/api/blacklist` | IP 黑名单管理 |
| 告警管理 | `/api/alert` | 告警处理 |
| 漏洞监测 | `/api/vulnerability` | 漏洞管理 |
| 用户管理 | `/api/user` | 用户管理 |
| 角色管理 | `/api/role` | 角色权限 |

## 数据库设计

### 核心数据表

| 表名 | 说明 |
|-----|-----|
| `sys_traffic_monitor` | 流量监测数据 |
| `sys_attack_monitor` | 攻击事件记录 |
| `sys_vulnerability_monitor` | 漏洞信息 |
| `sys_defense_log` | 防御日志 |
| `sys_monitor_rule` | 攻击检测规则 |
| `sys_ip_blacklist` | IP 黑名单 |
| `sys_alert` | 告警记录 |
| `sys_user` | 用户信息 |
| `sys_role` | 角色信息 |
| `sys_menu` | 菜单权限 |

### ER 图

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ sys_traffic_     │     │ sys_attack_      │     │ sys_defense_     │
│    monitor       │────▶│    monitor       │────▶│      log         │
└──────────────────┘     └──────────────────┘     └──────────────────┘
        │                        │
        │                        ▼
        │              ┌──────────────────┐
        │              │ sys_vulnerability│
        │              │    _monitor      │
        │              └──────────────────┘
        │
        ▼
┌──────────────────┐     ┌──────────────────┐
│ sys_monitor_rule │     │  sys_ip_blacklist│
└──────────────────┘     └──────────────────┘
```

## 配置说明

### 网关服务配置

```yaml
server:
  port: 9000

spring:
  cloud:
    gateway:
      routes:
        - id: target-service
          uri: lb://target-service
          predicates:
            - Path=/target/**

cross-service:
  security:
    enabled: true
    secret-key: "your-secret-key"
```

### 监测服务配置

```yaml
server:
  port: 9002

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/network_monitor
    username: root
    password: root

jwt:
  secret: your-jwt-secret-key
  expiration: 86400000

cross-service:
  auth:
    gateway-token: your-gateway-token
```
