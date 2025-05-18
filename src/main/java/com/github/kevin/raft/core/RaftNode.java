package com.github.kevin.raft.core;

import com.github.kevin.raft.common.ServerStatus;
import com.github.kevin.raft.entity.PersistentState;
import com.github.kevin.raft.netty.client.NettyClient;
import com.github.kevin.raft.netty.server.NettyServer;
import lombok.Data;

import java.util.List;

/**
 * 节点
 */
@Data
public class RaftNode {

    /**
     * 当前服务器节点的地址
     */
    private final String address;

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
     * 节点当前状态，初始化状态为follower
     *
     * @see ServerStatus
     */
    public volatile ServerStatus state = ServerStatus.FOLLOWER;

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
//        // 开启选举线程
//        electionScheduledExecutorService.scheduleWithFixedDelay(new ElectionThread(), 3000, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
//        // 开启心跳线程
//        heartbeatScheduledExecutorService.scheduleAtFixedRate(new HeartBeatThread(), 0, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
