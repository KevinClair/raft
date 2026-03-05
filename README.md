# Raft 分布式一致性算法实现

基于 Java + Spring Boot + Netty 实现的 Raft 分布式一致性算法，包含完整的领导者选举和日志复制功能。

## 功能特性

### 已实现功能

1. **领导者选举 (Leader Election)**
   - 随机选举超时机制，避免选举冲突
   - 基于任期（Term）的选举流程
   - 多数票选举机制
   - 自动故障检测和重新选举

2. **日志复制 (Log Replication)**
   - Leader 向 Follower 批量复制日志
   - 日志一致性检查
   - 基于多数派的日志提交
   - 自动日志冲突解决
   - 日志条目应用到状态机

3. **心跳机制 (Heartbeat)**
   - Leader 定期向 Follower 发送心跳
   - 防止不必要的选举
   - 携带日志提交信息

4. **状态管理**
   - 持久化状态：currentTerm, votedFor, log[]
   - 易失性状态：commitIndex, lastApplied
   - Leader 特有状态：nextIndex[], matchIndex[]

## 架构设计

### 核心组件

```
raft/
├── core/
│   ├── RaftNode.java              # Raft节点主类
│   ├── ConsensusModule.java       # 共识模块
│   ├── RaftRpcClient.java         # RPC客户端
│   └── RaftRpcClientContainer.java # RPC客户端容器
├── entity/
│   ├── LogEntry.java              # 日志条目
│   ├── LogModule.java             # 日志模块
│   ├── PersistentState.java       # 持久化状态
│   ├── VolatileState.java         # 易失性状态
│   └── StateMachine.java          # 状态机
├── dto/
│   ├── RequestVoteRequest.java    # 请求投票请求
│   ├── RequestVoteResponse.java   # 请求投票响应
│   ├── AppendEntriesRequest.java  # 追加日志请求
│   └── AppendEntriesResponse.java # 追加日志响应
├── netty/
│   ├── server/                    # Netty服务端
│   └── client/                    # Netty客户端
└── controller/
    └── RaftController.java        # REST API控制器
```

### 关键实现

#### 1. 领导者选举流程

```java
// 选举线程定期检查选举超时
if (status == ServerStatus.CANDIDATE) {
    // 1. 增加当前任期
    persistentState.setCurrentTerm(persistentState.getCurrentTerm() + 1);
    
    // 2. 投票给自己
    persistentState.setVotedFor(address);
    
    // 3. 向其他节点发送 RequestVote RPC
    // 4. 等待投票结果
    // 5. 如果获得多数票，成为 Leader
    if (success >= (1 + otherAddresses.size()) / 2) {
        status = ServerStatus.LEADER;
        becomeLeaderToDoThing();
    }
}
```

#### 2. 日志复制流程

```java
// Leader 定期向 Follower 发送 AppendEntries
for (int i = 0; i < otherAddresses.size(); i++) {
    // 1. 获取该 Follower 的 nextIndex
    Long nextIndex = volatileState.getNextIndex()[followerIndex];
    
    // 2. 准备要发送的日志条目
    List<LogEntry> entries = getEntriesSince(nextIndex);
    
    // 3. 发送 AppendEntries RPC
    // 4. 处理响应
    if (response.isSuccess()) {
        // 更新 nextIndex 和 matchIndex
        volatileState.getMatchIndex()[followerIndex] = newMatchIndex;
        volatileState.getNextIndex()[followerIndex] = newMatchIndex + 1;
    } else {
        // 减少 nextIndex 并重试
        volatileState.getNextIndex()[followerIndex]--;
    }
}

// 5. 尝试提交日志
tryCommitLog();
```

#### 3. 日志提交流程

```java
// 找到大多数节点已复制的最大索引
for (long n = lastLogIndex; n > commitIndex; n--) {
    // 统计已复制到多少个节点
    int replicaCount = 1; // leader自己
    for (int i = 0; i < matchIndex.length; i++) {
        if (matchIndex[i] >= n) {
            replicaCount++;
        }
    }
    
    // 如果大多数节点已复制，则提交
    if (replicaCount >= majority) {
        commitIndex = n;
        applyToStateMachine();
        break;
    }
}
```

## 使用方法

### 1. 配置

在 `application.properties` 中配置节点信息：

```properties
# 当前节点地址
raft.selfAddress=127.0.0.1:27015
# Netty 端口
raft.port=27015
# 其他节点地址列表
raft.address=127.0.0.1:27016,127.0.0.1:27017,127.0.0.1:27018,127.0.0.1:27019
# 是否启用选举
raft.startElection=true
# Spring Boot 端口
server.port=8081
```

### 2. 启动节点

```bash
mvn spring-boot:run
```

### 3. API 使用

#### 查看节点状态

```bash
curl http://localhost:8081/raft/status
```

响应示例：
```json
{
  "address": "127.0.0.1:27015",
  "status": "LEADER",
  "currentTerm": 1,
  "leaderId": "127.0.0.1:27015",
  "commitIndex": 3,
  "lastApplied": 3,
  "lastLogIndex": 3
}
```

#### 提交日志（仅 Leader）

```bash
curl -X POST "http://localhost:8081/raft/log?command=SET_KEY&data=value"
```

响应示例：
```json
{
  "success": true,
  "message": "Log appended successfully",
  "lastLogIndex": 4
}
```

#### 读取日志

```bash
curl http://localhost:8081/raft/log/1
```

响应示例：
```json
{
  "success": true,
  "index": 1,
  "term": 1,
  "command": "SET_KEY",
  "data": "value"
}
```

## 配置参数

### Raft 相关参数

- `heartBeatTick`: 心跳间隔基数，默认 50ms
- `electionTime`: 选举超时基数，默认 150ms
- 实际选举超时 = electionTime + random(0, electionTime)

### 线程池配置

- `electionScheduledExecutorService`: 选举线程池，1个线程
- `heartbeatScheduledExecutorService`: 心跳线程池，1个线程

## Raft 算法特性

### 安全性保证

1. **选举安全性 (Election Safety)**
   - 在给定任期内，最多只有一个 Leader 被选举出来

2. **Leader 仅追加原则 (Leader Append-Only)**
   - Leader 永远不会覆盖或删除自己的日志，只会追加新日志

3. **日志匹配原则 (Log Matching)**
   - 如果两个日志条目有相同的索引和任期，则它们存储相同的命令
   - 如果两个日志条目有相同的索引和任期，则它们之前的所有日志条目都相同

4. **Leader 完整性 (Leader Completeness)**
   - 如果一个日志条目在某个任期被提交，则该条目必然出现在所有更高任期的 Leader 的日志中

5. **状态机安全性 (State Machine Safety)**
   - 如果一个节点在某个索引位置应用了日志条目到状态机，则其他节点不会在相同索引位置应用不同的日志条目

### 活性保证

- 使用随机选举超时避免选举分裂
- 心跳机制保持 Leader 的权威性
- 自动重试机制处理网络故障

## 测试

详细的测试指南请参考 [TESTING.md](TESTING.md)。

### 快速测试

1. 启动至少 3 个节点
2. 观察 Leader 选举
3. 向 Leader 提交日志
4. 验证日志复制到所有节点
5. 停止 Leader 观察重新选举

## 性能考虑

### 优化点

1. **批量复制**：一次 AppendEntries 可以发送多个日志条目
2. **异步复制**：使用 CompletableFuture 异步发送 RPC
3. **并发控制**：使用锁保护共享状态

### 性能参数

- 心跳间隔：50ms（可调整）
- 选举超时：150-300ms（随机，可调整）
- RPC 超时：3秒

## 局限性和未来改进

### 当前局限性

1. 日志存储使用内存（HashMap），未持久化到磁盘
2. 未实现日志压缩和快照
3. 未实现集群成员变更
4. 未实现只读优化

### 未来改进方向

1. **持久化**：将日志和状态持久化到文件或数据库
2. **快照**：实现日志压缩和快照传输
3. **成员变更**：支持动态添加/删除节点
4. **性能优化**：
   - 流水线复制
   - 批处理优化
   - 只读请求优化
5. **监控**：添加详细的监控和指标

## 参考资料

- [In Search of an Understandable Consensus Algorithm (Extended Version)](https://raft.github.io/raft.pdf)
- [Raft Consensus Algorithm](https://raft.github.io/)
- [The Raft Consensus Algorithm](https://raft.github.io/)

## 许可证

本项目遵循项目原有的许可证。

## 贡献

欢迎提交 Issue 和 Pull Request。

## 作者

KevinClair
