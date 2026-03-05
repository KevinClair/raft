# Raft算法实现测试指南

本文档说明如何测试Raft算法的领导者选举和日志复制功能。

## 环境准备

本Raft实现需要至少3个节点来形成集群。建议使用5个节点以获得更好的容错性。

## 配置说明

每个节点需要在`application.properties`或`application.yml`中配置：

```properties
# 节点1 - 端口27015
raft.selfAddress=127.0.0.1:27015
raft.port=27015
raft.address=127.0.0.1:27016,127.0.0.1:27017,127.0.0.1:27018,127.0.0.1:27019
raft.startElection=true
server.port=8081

# 节点2 - 端口27016
raft.selfAddress=127.0.0.1:27016
raft.port=27016
raft.address=127.0.0.1:27015,127.0.0.1:27017,127.0.0.1:27018,127.0.0.1:27019
raft.startElection=true
server.port=8082

# 节点3 - 端口27017
raft.selfAddress=127.0.0.1:27017
raft.port=27017
raft.address=127.0.0.1:27015,127.0.0.1:27016,127.0.0.1:27018,127.0.0.1:27019
raft.startElection=true
server.port=8083

# 节点4 - 端口27018
raft.selfAddress=127.0.0.1:27018
raft.port=27018
raft.address=127.0.0.1:27015,127.0.0.1:27016,127.0.0.1:27017,127.0.0.1:27019
raft.startElection=true
server.port=8084

# 节点5 - 端口27019
raft.selfAddress=127.0.0.1:27019
raft.port=27019
raft.address=127.0.0.1:27015,127.0.0.1:27016,127.0.0.1:27017,127.0.0.1:27018
raft.startElection=true
server.port=8085
```

## 测试步骤

### 1. 领导者选举测试

1. **启动所有节点**
   ```bash
   # 在不同的终端窗口启动每个节点
   mvn spring-boot:run -Dspring-boot.run.arguments="--raft.selfAddress=127.0.0.1:27015 --raft.port=27015 --server.port=8081"
   mvn spring-boot:run -Dspring-boot.run.arguments="--raft.selfAddress=127.0.0.1:27016 --raft.port=27016 --server.port=8082"
   mvn spring-boot:run -Dspring-boot.run.arguments="--raft.selfAddress=127.0.0.1:27017 --raft.port=27017 --server.port=8083"
   mvn spring-boot:run -Dspring-boot.run.arguments="--raft.selfAddress=127.0.0.1:27018 --raft.port=27018 --server.port=8084"
   mvn spring-boot:run -Dspring-boot.run.arguments="--raft.selfAddress=127.0.0.1:27019 --raft.port=27019 --server.port=8085"
   ```

2. **检查节点状态**
   ```bash
   # 查看每个节点的状态
   curl http://localhost:8081/raft/status
   curl http://localhost:8082/raft/status
   curl http://localhost:8083/raft/status
   curl http://localhost:8084/raft/status
   curl http://localhost:8085/raft/status
   ```

   **预期结果：**
   - 应该有一个节点的status为"LEADER"
   - 其他节点的status为"FOLLOWER"
   - 所有节点的currentTerm应该相同
   - 所有节点的leaderId应该指向同一个leader节点

3. **观察日志输出**
   - 查看日志中的选举过程
   - 应该能看到类似以下的日志：
     ```
     Node 127.0.0.1:27015 will become CANDIDATE and start election leader
     Node 127.0.0.1:27015 received votes, success count = 3
     Node 127.0.0.1:27015 become leader successfully
     ```

### 2. 日志复制测试

1. **向Leader提交日志**
   
   首先找到Leader节点（假设是端口8081），然后提交一些命令：
   
   ```bash
   # 提交第一条日志
   curl -X POST "http://localhost:8081/raft/log?command=SET_KEY_1&data=value1"
   
   # 提交第二条日志
   curl -X POST "http://localhost:8081/raft/log?command=SET_KEY_2&data=value2"
   
   # 提交第三条日志
   curl -X POST "http://localhost:8081/raft/log?command=SET_KEY_3&data=value3"
   ```

   **预期结果：**
   ```json
   {
     "success": true,
     "message": "Log appended successfully",
     "lastLogIndex": 1
   }
   ```

2. **验证日志复制**
   
   等待几秒钟让日志复制到所有节点，然后检查所有节点的状态：
   
   ```bash
   # 检查所有节点
   curl http://localhost:8081/raft/status
   curl http://localhost:8082/raft/status
   curl http://localhost:8083/raft/status
   curl http://localhost:8084/raft/status
   curl http://localhost:8085/raft/status
   ```

   **预期结果：**
   - 所有节点的lastLogIndex应该相同
   - 所有节点的commitIndex应该相同
   - Leader节点应该显示日志已提交

3. **读取日志条目**
   
   ```bash
   # 从不同节点读取日志
   curl http://localhost:8081/raft/log/1
   curl http://localhost:8082/raft/log/1
   curl http://localhost:8083/raft/log/1
   ```

   **预期结果：**
   所有节点应该返回相同的日志内容：
   ```json
   {
     "success": true,
     "index": 1,
     "term": 1,
     "command": "SET_KEY_1",
     "data": "value1"
   }
   ```

### 3. Leader故障转移测试

1. **停止当前Leader节点**
   
   找到当前的Leader并停止它（Ctrl+C）

2. **观察新Leader选举**
   
   等待几秒钟，然后检查剩余节点的状态：
   
   ```bash
   # 假设停止了8081，检查其他节点
   curl http://localhost:8082/raft/status
   curl http://localhost:8083/raft/status
   curl http://localhost:8084/raft/status
   curl http://localhost:8085/raft/status
   ```

   **预期结果：**
   - 应该有一个新的Leader被选举出来
   - currentTerm应该增加
   - 所有存活节点应该认可新的Leader

3. **向新Leader提交日志**
   
   ```bash
   # 假设新Leader是8082
   curl -X POST "http://localhost:8082/raft/log?command=SET_KEY_4&data=value4"
   ```

   **预期结果：**
   - 新日志应该成功提交
   - 日志应该复制到大多数节点

4. **重启原Leader节点**
   
   重启之前停止的节点，它应该：
   - 作为Follower重新加入集群
   - 同步所有缺失的日志
   - 认可当前的Leader

### 4. 网络分区测试

1. **模拟网络分区**
   
   通过防火墙规则或手动停止一些节点来模拟网络分区：
   
   - 保持3个节点在线（多数派）
   - 停止2个节点（少数派）

2. **验证多数派继续工作**
   
   ```bash
   # 向多数派中的Leader提交日志
   curl -X POST "http://localhost:8081/raft/log?command=PARTITION_TEST&data=test_data"
   ```

   **预期结果：**
   - 多数派应该能够继续选举Leader
   - 应该能够提交和复制新日志

3. **恢复网络分区**
   
   重启停止的节点
   
   **预期结果：**
   - 停止的节点应该同步到最新状态
   - 所有节点应该达到一致性

## 预期行为总结

### 领导者选举
1. 集群启动后，在15秒内应该选举出一个Leader
2. Leader会定期发送心跳（每50ms）
3. 如果Follower在选举超时时间内没有收到心跳，会发起新的选举
4. 只有获得大多数投票的候选者才能成为Leader

### 日志复制
1. 只有Leader可以接收客户端的日志条目
2. Leader将日志条目复制到所有Follower
3. 当大多数节点复制了日志条目后，Leader会提交该条目
4. 已提交的日志条目会应用到状态机

### 一致性保证
1. 所有节点最终会达到相同的日志状态
2. 已提交的日志不会丢失
3. Leader只会追加日志，不会覆盖已有日志

## 常见问题

### Q: 节点无法选举出Leader
A: 确保：
- 至少有3个节点在运行
- 所有节点的配置正确
- 网络连接正常
- 检查日志中的错误信息

### Q: 日志复制失败
A: 检查：
- Leader节点是否正常运行
- Follower节点是否与Leader建立了连接
- 检查matchIndex和nextIndex的值

### Q: 节点状态不一致
A: 这是正常的：
- 在网络分区恢复后，可能需要一些时间达到一致
- 检查commitIndex，已提交的日志应该是一致的

## 性能测试

可以使用以下脚本进行批量测试：

```bash
#!/bin/bash
# 批量提交日志
for i in {1..100}
do
  curl -X POST "http://localhost:8081/raft/log?command=TEST_$i&data=data_$i"
  sleep 0.1
done
```

观察：
- 日志复制的延迟
- 系统的吞吐量
- 在不同负载下的表现
