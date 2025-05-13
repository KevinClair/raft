package com.github.kevin.raft.core;

import java.util.HashMap;
import java.util.Map;

public class RaftRpcClientContainer {

    /**
     * 构建一个Map，key为节点的ip地址，value为对应节点的RpcClient对象
     */
    private Map<String, RaftRpcClient> rpcClients = new HashMap<>();

    public static RaftRpcClientContainer getInstance() {
        return RaftRpcClientContainerHolder.INSTANCE;
    }

    private static class RaftRpcClientContainerHolder {
        private static final RaftRpcClientContainer INSTANCE = new RaftRpcClientContainer();
    }

    /**
     * 添加Rpc客户端
     *
     * @param address   节点地址
     * @param rpcClient 节点RpcClient对象
     */
    public void addRpcClient(String address, RaftRpcClient rpcClient) {
        rpcClients.put(address, rpcClient);
    }

    /**
     * 获取Rpc客户端
     *
     * @param address 节点地址
     * @return 节点RpcClient对象
     */
    public RaftRpcClient getRpcClient(String address) {
        return rpcClients.get(address);
    }

    /**
     * 获取所有Rpc客户端
     *
     * @return 节点RpcClient对象集合
     */
    public Map<String, RaftRpcClient> getAllRpcClients() {
        return rpcClients;
    }

    /**
     * 删除Rpc客户端
     *
     * @param address 节点地址
     */
    public void removeRpcClient(String address) {
        rpcClients.remove(address);
    }

    /**
     * 判断是否包含某个Rpc客户端
     *
     * @param address 节点地址
     * @return 是否包含
     */
    public Boolean contains(String address) {
        return rpcClients.containsKey(address);
    }

}

