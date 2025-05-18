package com.github.kevin.raft.core;

import com.github.kevin.raft.common.ServerStatus;
import com.github.kevin.raft.dto.AppendEntriesRequest;
import com.github.kevin.raft.dto.Request;
import com.github.kevin.raft.entity.PersistentState;
import com.github.kevin.raft.entity.VolatileState;
import com.github.kevin.raft.netty.client.NettyClient;
import com.github.kevin.raft.netty.server.NettyServer;
import com.github.kevin.raft.utils.NameThreadPoolFactory;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * 节点
 */
@Data
public class RaftNode {

    /**
     * 当前服务器节点的地址
     */
    private final String address;

    private String leaderAddress;

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

    private final VolatileState volatileState;

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
     * 心跳间隔基数
     */
    private final long heartBeatTick = 5 * 100;

    private ScheduledExecutorService electionScheduledExecutorService = new ScheduledThreadPoolExecutor(1, new NameThreadPoolFactory("election"));
    private ScheduledExecutorService heartbeatScheduledExecutorService = new ScheduledThreadPoolExecutor(1, new NameThreadPoolFactory("heartbeat"));

    public RaftNode(String address, List<String> otherAddresses, Integer port) {
        this.address = address;
        this.otherAddresses = otherAddresses;
        this.consensusModule = new ConsensusModule(this);
        // 启动服务端
        new NettyServer(port);
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
        // 开启选举线程
        electionScheduledExecutorService.scheduleWithFixedDelay(new ElectionThread(), 3000, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
        // 开启心跳线程
        heartbeatScheduledExecutorService.scheduleAtFixedRate(new HeartBeatThread(), 0, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 选举线程
     *
     * @author KevinClair
     **/
    private class ElectionThread implements Runnable {

        @Override
        public void run() {

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

            // 向所有的follower节点发送心跳
            otherAddresses.forEach(this::sendHeartBeat);
        }

        private void sendHeartBeat(String address) {
            Optional.ofNullable(RaftRpcClientContainer.getInstance().getRpcClient(address))
                    .ifPresent(rpcClient -> {
                        AppendEntriesRequest appendEntriesRequest = AppendEntriesRequest.builder().entries(null).leaderAddress(address).serverAddress(address).currentTerm(persistentState.getCurrentTerm()).leaderCommit(volatileState.getCommitIndex()).build();
                        Request<AppendEntriesRequest> request = new Request<>(Request.A_ENTRIES, appendEntriesRequest);
                        rpcClient.send(request);
                    });
        }
    }
}
