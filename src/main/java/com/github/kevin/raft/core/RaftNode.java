package com.github.kevin.raft.core;

import com.github.kevin.raft.common.ServerStatus;
import com.github.kevin.raft.dto.AppendEntriesRequest;
import com.github.kevin.raft.dto.AppendEntriesResponse;
import com.github.kevin.raft.dto.RequestVoteRequest;
import com.github.kevin.raft.dto.RequestVoteResponse;
import com.github.kevin.raft.entity.*;
import com.github.kevin.raft.netty.client.NettyClient;
import com.github.kevin.raft.netty.common.constants.MessageTypeEnum;
import com.github.kevin.raft.netty.common.entity.RaftMessage;
import com.github.kevin.raft.netty.server.NettyServer;
import com.github.kevin.raft.utils.NameThreadPoolFactory;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 节点
 */
@Slf4j
@Data
public class RaftNode {

    /**
     * 当前服务器节点的地址
     */
    private final String address;

    /**
     * leader节点的地址
     */
    private String leaderId;

    /**
     * 其他节点的地址
     */
    private final List<String> otherAddresses;

    /**
     * 一致性模块
     */
    private final ConsensusModule consensusModule;

    /**
     * RPC客户端
     */
//    private final RaftRpcClient rpcClient;

    /**
     * 持久化状态
     */
    private final PersistentState persistentState;

    /**
     * 易失性状态
     */
    private final VolatileState volatileState;

    /**
     * 日志模块
     */
    private final LogModule logModule;

    /**
     * 状态机
     */
    private final StateMachine stateMachine;

    /**
     * 节点当前状态，初始化状态为follower
     *
     * @see ServerStatus
     */
    private volatile ServerStatus status = ServerStatus.FOLLOWER;

    /**
     * 上次一心跳时间戳
     */
    private volatile long preHeartBeatTime = 0;

    /**
     * 上一次选举时间
     */
    public volatile long preElectionTime = 0;

    /**
     * 心跳间隔基数
     */
    private final long heartBeatTick = 5 * 10;

    /**
     * 选举时间间隔基数
     */
    public volatile long electionTime = 15 * 10;

    private ScheduledExecutorService electionScheduledExecutorService = new ScheduledThreadPoolExecutor(1, new NameThreadPoolFactory("election"));
    private ScheduledExecutorService heartbeatScheduledExecutorService = new ScheduledThreadPoolExecutor(1, new NameThreadPoolFactory("heartbeat"));

    public RaftNode(String address, List<String> otherAddresses, Integer port, Boolean startElection) {
        this.address = address;
        this.otherAddresses = otherAddresses;
        this.consensusModule = new ConsensusModule(this);
        // 启动服务端
        new NettyServer(port, this);
        // 延迟3秒启动客户端
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 启动客户端
        NettyClient nettyClient = new NettyClient();
        otherAddresses.forEach(nettyClient::connect);
        this.persistentState = new PersistentState();
        this.volatileState = new VolatileState(otherAddresses.size() + 1);
        this.logModule = new LogModule();
        this.stateMachine = new StateMachine();
        // 开启选举线程
        if (startElection) {
            electionScheduledExecutorService.scheduleWithFixedDelay(new ElectionThread(), 3000, 3000, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        // 开启心跳线程
        heartbeatScheduledExecutorService.scheduleAtFixedRate(new HeartBeatThread(), 0, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 选举线程
     *
     * @author KevinClair
     **/
    private class ElectionThread implements Runnable {

        @Override
        public void run() {

            try {
                if (status == ServerStatus.LEADER) {
                    return;
                }

                long current = System.currentTimeMillis();
                // 基于 RAFT 的随机时间,解决冲突.
                long newElectionTime = electionTime + ThreadLocalRandom.current().nextLong(electionTime);
                if (current - preElectionTime < newElectionTime) {
                    return;
                }

                // 变更当前节点状态
                status = ServerStatus.CANDIDATE;
                log.error("Node {} will become CANDIDATE and start election leader, current term : [{}], LastEntry : [{}]",
                        address, persistentState.getCurrentTerm(), logModule.getLastIndex());
                // 变更上一次选举时间
                preElectionTime = System.currentTimeMillis() + newElectionTime;

                // 将当前任期加1
                persistentState.setCurrentTerm(persistentState.getCurrentTerm() + 1);

                // 推荐自己.
                persistentState.setVotedFor(address);

                List<CompletableFuture<RequestVoteResponse>> futureArrayList = new ArrayList<>();
                AtomicInteger successVote = new AtomicInteger(1);
                for (String otherAddress : otherAddresses) {
                    Long lastTerm = Optional.ofNullable(logModule.getLast()).map(LogEntry::getTerm).orElse(0L);
                    RequestVoteRequest requestVoteRequest = RequestVoteRequest.builder().term(persistentState.getCurrentTerm()).candidateId(address).lastLogIndex(logModule.getLastIndex()).lastLogTerm(lastTerm).build();
                    RaftMessage<RequestVoteRequest> request = RaftMessage.<RequestVoteRequest>builder()
                            .type(MessageTypeEnum.VOTE_REQUEST)
                            .data(requestVoteRequest)
                            .build();
                    futureArrayList.add(this.sendElection(otherAddress, request, successVote));
                }
                // 等待投票结果
                CompletableFuture<Void> completableFuture = CompletableFuture.allOf(futureArrayList.toArray(new CompletableFuture[futureArrayList.size()]));
                try {
                    completableFuture.get(newElectionTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }
                // 获取投票结果
                int success = successVote.get();
                log.info("Node {} received votes, success count = {}, current term: {}", address, success, persistentState.getCurrentTerm());
                // 如果投票期间,有其他服务器发送 appendEntry , 就可能变成 follower ,这时,应该停止.
                if (status == ServerStatus.FOLLOWER) {
                    return;
                }
                log.info("Node {} received votes, success count = {}", address, success);

                // 判断是否成为leader
                if (success >= (1 + otherAddresses.size()) / 2) {
                    log.warn("Node {} become leader successfully, current term: {}", address, persistentState.getCurrentTerm());
                    status = ServerStatus.LEADER;
                    // 设置leader地址
                    leaderId = address;
                    // 清空投票记录
                    persistentState.setVotedFor(null);
                    // 执行成为leader后的操作
                    becomeLeaderToDoThing();
                } else {
                    log.error("Node {} failed to become leader, current term: {}, success count: {}", address, persistentState.getCurrentTerm(), success);
                    // 重新开始下一轮选举
                    persistentState.setVotedFor(null);
                }
                // 再次更新选举时间
                preElectionTime = System.currentTimeMillis() + newElectionTime;
            } catch (RuntimeException e) {
                log.error(e.getMessage(), e);
            }

        }

        private CompletableFuture<RequestVoteResponse> sendElection(String address, RaftMessage<RequestVoteRequest> request, AtomicInteger success) {
            // todo 调整为异步方式
            return Optional.ofNullable(RaftRpcClientContainer.getInstance().getRpcClient(address))
                    .map(rpcClient -> {
                        CompletableFuture<RequestVoteResponse> requestVoteResponseCompletableFuture = rpcClient.handleRequestVote(request);
                        return requestVoteResponseCompletableFuture.whenComplete((requestVoteResponse, throwable) -> {
                            Boolean voteGranted = requestVoteResponse.getVoteGranted();
                            if (voteGranted) {
                                log.info("Node {} received successfully vote from {}, current term: {}", address, request.getData().getCandidateId(), persistentState.getCurrentTerm());
                                // 投票成功，成功票数增加
                                success.incrementAndGet();
                            } else {
                                // 投票失败，判断是否是因为term过期
                                Long term = requestVoteResponse.getTerm();
                                if (term > persistentState.getCurrentTerm()) {
                                    log.error("Received vote from {} with higher term: {}, current term: {}", request.getData().getCandidateId(), term, persistentState.getCurrentTerm());
                                    // 如果收到的term比当前节点的term大，则更新当前节点的term
                                    persistentState.setCurrentTerm(term);
                                }
                            }
                            RequestFutureManager.remove(request.getRequestId());
                        });
                    }).orElseGet(() -> CompletableFuture.completedFuture(RequestVoteResponse.fail()));
        }
    }

    /**
     * 成为Leader后的初始化操作
     */
    private void becomeLeaderToDoThing() {
        // 初始化 nextIndex 和 matchIndex
        Long lastIndex = logModule.getLastIndex();
        for (int i = 0; i < otherAddresses.size(); i++) {
            volatileState.getNextIndex()[i] = lastIndex + 1;
            volatileState.getMatchIndex()[i] = 0L;
        }
        log.info("Leader {} initialized nextIndex and matchIndex arrays", address);
    }

    /**
     * 心跳线程
     *
     * @author KevinClair
     **/
    @RequiredArgsConstructor
    private class HeartBeatThread implements Runnable {

        @Override
        public void run() {
            try {
                // 不是leader节点，直接跳过
                if (status != ServerStatus.LEADER) {
                    return;
                }

                // 如果上一次心跳间隔，和当前时间的差值小于心跳间隔基数，则不发送心跳
                long currentTime = System.currentTimeMillis();
                if (currentTime - preHeartBeatTime < heartBeatTick) {
                    return;
                }

                // 更新心跳时间
                preHeartBeatTime = currentTime;

                // 向所有的follower节点发送心跳或日志复制请求
                for (int i = 0; i < otherAddresses.size(); i++) {
                    String followerAddress = otherAddresses.get(i);
                    int followerIndex = i;
                    // 异步发送给每个follower
                    this.sendAppendEntries(followerAddress, followerIndex);
                }

                // 尝试提交日志
                tryCommitLog();
            } catch (Exception e) {
                log.error("HeartBeat thread error: {}", e.getMessage(), e);
            }
        }

        private void sendAppendEntries(String followerAddress, int followerIndex) {
            Optional.ofNullable(RaftRpcClientContainer.getInstance().getRpcClient(followerAddress))
                    .ifPresent(rpcClient -> {
                        try {
                            // 获取该follower的nextIndex
                            Long nextIndex = volatileState.getNextIndex()[followerIndex];
                            Long prevLogIndex = nextIndex - 1;
                            Long prevLogTerm = 0L;

                            // 获取prevLogTerm
                            if (prevLogIndex > 0) {
                                LogEntry prevLog = logModule.read(prevLogIndex);
                                if (prevLog != null) {
                                    prevLogTerm = prevLog.getTerm();
                                }
                            }

                            // 准备要发送的日志条目
                            List<LogEntry> entries = new ArrayList<>();
                            Long lastLogIndex = logModule.getLastIndex();
                            
                            // 如果有新日志需要复制
                            if (nextIndex <= lastLogIndex) {
                                // 发送从nextIndex开始的日志条目
                                for (long i = nextIndex; i <= lastLogIndex; i++) {
                                    LogEntry entry = logModule.read(i);
                                    if (entry != null) {
                                        entries.add(entry);
                                    }
                                }
                            }

                            // 构建AppendEntries请求
                            AppendEntriesRequest appendEntriesRequest = AppendEntriesRequest.builder()
                                    .currentTerm(persistentState.getCurrentTerm())
                                    .leaderId(address)
                                    .previousLogIndex(prevLogIndex)
                                    .previousLogTerm(prevLogTerm)
                                    .entries(entries.isEmpty() ? null : entries)
                                    .leaderCommit(volatileState.getCommitIndex())
                                    .build();

                            RaftMessage<AppendEntriesRequest> request = RaftMessage.<AppendEntriesRequest>builder()
                                    .type(MessageTypeEnum.APPEND_ENTRIES_REQUEST)
                                    .data(appendEntriesRequest)
                                    .build();

                            // 发送请求并处理响应
                            CompletableFuture<AppendEntriesResponse> future = rpcClient.handleAppendEntries(request);
                            future.whenComplete((response, throwable) -> {
                                try {
                                    if (throwable != null) {
                                        log.error("Failed to send AppendEntries to {}: {}", followerAddress, throwable.getMessage());
                                        return;
                                    }

                                    if (response == null) {
                                        return;
                                    }

                                    // 检查返回的term
                                    if (response.getTerm() > persistentState.getCurrentTerm()) {
                                        log.warn("Received higher term {} from {}, stepping down", response.getTerm(), followerAddress);
                                        persistentState.setCurrentTerm(response.getTerm());
                                        persistentState.setVotedFor(null);
                                        status = ServerStatus.FOLLOWER;
                                        return;
                                    }

                                    // 只有leader才处理响应
                                    if (status != ServerStatus.LEADER) {
                                        return;
                                    }

                                    if (response.isSuccess()) {
                                        // 成功：更新nextIndex和matchIndex
                                        if (!entries.isEmpty()) {
                                            long newMatchIndex = prevLogIndex + entries.size();
                                            volatileState.getMatchIndex()[followerIndex] = newMatchIndex;
                                            volatileState.getNextIndex()[followerIndex] = newMatchIndex + 1;
                                            log.info("Successfully replicated logs to {}, matchIndex: {}, nextIndex: {}",
                                                    followerAddress, newMatchIndex, newMatchIndex + 1);
                                        }
                                    } else {
                                        // 失败：减少nextIndex并重试
                                        if (volatileState.getNextIndex()[followerIndex] > 1) {
                                            volatileState.getNextIndex()[followerIndex]--;
                                            log.warn("AppendEntries failed for {}, decreasing nextIndex to {}",
                                                    followerAddress, volatileState.getNextIndex()[followerIndex]);
                                        }
                                    }
                                } finally {
                                    RequestFutureManager.remove(request.getRequestId());
                                }
                            });
                        } catch (Exception e) {
                            log.error("Error sending AppendEntries to {}: {}", followerAddress, e.getMessage(), e);
                        }
                    });
        }

        /**
         * 尝试提交日志
         */
        private void tryCommitLog() {
            if (status != ServerStatus.LEADER) {
                return;
            }

            // 找到大多数节点已复制的最大索引
            Long lastLogIndex = logModule.getLastIndex();
            for (long n = lastLogIndex; n > volatileState.getCommitIndex(); n--) {
                // 检查索引n的日志是否在当前任期内创建
                LogEntry logEntry = logModule.read(n);
                if (logEntry == null || logEntry.getTerm() != persistentState.getCurrentTerm()) {
                    continue;
                }

                // 统计已复制到多少个节点
                int replicaCount = 1; // leader自己
                for (int i = 0; i < volatileState.getMatchIndex().length; i++) {
                    if (volatileState.getMatchIndex()[i] >= n) {
                        replicaCount++;
                    }
                }

                // 如果大多数节点已复制，则提交
                int majority = (otherAddresses.size() + 1) / 2 + 1;
                if (replicaCount >= majority) {
                    log.info("Committing log entries up to index {}, replicated on {} nodes", n, replicaCount);
                    volatileState.setCommitIndex(n);
                    
                    // 应用已提交但未应用的日志到状态机
                    for (long i = volatileState.getLastApplied() + 1; i <= volatileState.getCommitIndex(); i++) {
                        LogEntry entry = logModule.read(i);
                        if (entry != null) {
                            stateMachine.apply(entry);
                            volatileState.setLastApplied(i);
                            log.info("Applied log entry {} to state machine", i);
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * 接收客户端命令并复制到集群
     *
     * @param command 命令
     * @param data    数据
     * @return 是否成功
     */
    public boolean appendLog(String command, byte[] data) {
        if (status != ServerStatus.LEADER) {
            log.warn("Node {} is not leader, cannot append log", address);
            return false;
        }

        try {
            // 创建新的日志条目
            LogEntry logEntry = new LogEntry();
            logEntry.setTerm(persistentState.getCurrentTerm());
            logEntry.setCommand(command);
            logEntry.setData(data);

            // 写入本地日志
            logModule.write(logEntry);
            log.info("Leader {} appended new log entry at index {}", address, logEntry.getIndex());

            return true;
        } catch (Exception e) {
            log.error("Failed to append log: {}", e.getMessage(), e);
            return false;
        }
    }
}
