package com.github.kevin.raft.core;

import com.github.kevin.raft.dto.*;
import com.github.kevin.raft.netty.common.entity.RaftMessage;
import io.netty.channel.Channel;

import java.util.concurrent.CompletableFuture;

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

    public CompletableFuture<AppendEntriesResponse> handleAppendEntries(RaftMessage<AppendEntriesRequest> request) {
        // 处理成功的响应
        CompletableFuture<RaftMessage> completableFuture = new CompletableFuture<>();
        channel.writeAndFlush(request).addListener(future -> {
            if (future.isSuccess()) {
                RequestFutureManager.putTask(request.getRequestId(), completableFuture);
            }
        });
        return completableFuture.thenApply(response -> (AppendEntriesResponse) response.getData());
    }

    public CompletableFuture<RequestVoteResponse> handleRequestVote(RaftMessage<RequestVoteRequest> request) {
        // 处理成功的响应
        CompletableFuture<RaftMessage> completableFuture = new CompletableFuture<>();
        channel.writeAndFlush(request).addListener(future -> {
            if (future.isSuccess()) {
                RequestFutureManager.putTask(request.getRequestId(), completableFuture);
            }
        });
        return completableFuture.thenApply(response -> (RequestVoteResponse) response.getData());
    }
}
