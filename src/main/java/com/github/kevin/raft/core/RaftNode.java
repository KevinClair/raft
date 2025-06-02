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
//        heartbeatScheduledExecutorService.scheduleAtFixedRate(new HeartBeatThread(), 0, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
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
                    //                becomeLeaderToDoThing();
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
     * 心跳线程
     *
     * @author KevinClair
     **/
    @RequiredArgsConstructor
    private class HeartBeatThread implements Runnable {

        @Override
        public void run() {
            // 不是leader节点，直接跳过
            if (status != ServerStatus.LEADER) {
                return;
            }

            // 如果上一次心跳间隔，和当前时间的差值小于心跳间隔基数，则不发送心跳
            long currentTime = System.currentTimeMillis();
            if (currentTime - preHeartBeatTime < heartBeatTick) {
                return;
            }
            AppendEntriesRequest appendEntriesRequest = AppendEntriesRequest.builder().entries(null).leaderId(address).currentTerm(persistentState.getCurrentTerm()).leaderCommit(volatileState.getCommitIndex()).build();
            // todo 修改此处的requestId的生成方式
            RaftMessage<AppendEntriesRequest> request = new RaftMessage<>(new Random().nextInt(), MessageTypeEnum.APPEND_ENTRIES_REQUEST, appendEntriesRequest);
            // 向所有的follower节点发送心跳
            otherAddresses.forEach(address -> {
                // 发送心跳
                this.sendHeartBeat(address, request);
            });
        }

        private void sendHeartBeat(String address, RaftMessage<AppendEntriesRequest> request) {
            // todo 调整为异步方式
            Optional.ofNullable(RaftRpcClientContainer.getInstance().getRpcClient(address))
                    .ifPresent(rpcClient -> {
                        CompletableFuture<AppendEntriesResponse> appendEntriesResponseCompletableFuture = rpcClient.handleAppendEntries(request);
                        try {
                            AppendEntriesResponse appendEntriesResponse = appendEntriesResponseCompletableFuture.get(3000, TimeUnit.MILLISECONDS);
                            Long term = appendEntriesResponse.getTerm();
                            if (term > persistentState.getCurrentTerm()) {
                                log.error("Received heartbeat from {} with higher term: {}, current term: {}", address, term, persistentState.getCurrentTerm());
                                // 如果收到的term比当前节点的term大，则更新当前节点的term
                                persistentState.setCurrentTerm(term);
                                persistentState.setVotedFor(null);
                                // 更新状态为follower
                                status = ServerStatus.FOLLOWER;
                            }
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            throw new RuntimeException(e);
                        } finally {
                            RequestFutureManager.remove(request.getRequestId());
                        }
                    });
        }
    }
}
