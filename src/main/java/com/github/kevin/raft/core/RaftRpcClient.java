package com.github.kevin.raft.core;

import com.github.kevin.raft.dto.RequestVoteReqDto;
import io.netty.channel.Channel;

public class RaftRpcClient {

    private final Channel channel;

    public RaftRpcClient(Channel channel) {
        this.channel = channel;
    }

    /**
     * 发送请求投票RPC
     */
    public void requestVote(RequestVoteReqDto args) {
        // 使用channel发送请求
        channel.writeAndFlush(args);
    }
}
