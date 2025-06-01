package com.github.kevin.raft.netty.server;

import com.github.kevin.raft.core.RaftNode;
import com.github.kevin.raft.dto.AppendEntriesRequest;
import com.github.kevin.raft.dto.AppendEntriesResponse;
import com.github.kevin.raft.dto.RequestVoteRequest;
import com.github.kevin.raft.dto.RequestVoteResponse;
import com.github.kevin.raft.netty.common.entity.RaftMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty服务端处理器
 *
 * @author KevinClair
 **/
@ChannelHandler.Sharable
@RequiredArgsConstructor
@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<RaftMessage<Object>> {

    private final RaftNode raftNode;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RaftMessage<Object> request) {
        // 根据不同的消息类型进行处理
        switch (request.getType()) {
            case APPEND_ENTRIES_REQUEST:
                // 处理追加日志请求
                log.info("Received AppendEntriesRequest: {}", request.getData());
                AppendEntriesResponse appendEntriesResponse = raftNode.getConsensusModule().handleAppendEntries((AppendEntriesRequest) request.getData());
                // 发送响应
                ctx.channel().writeAndFlush(RaftMessage.builder().type(request.getType()).requestId(request.getRequestId()).data(appendEntriesResponse).build());
                break;
            case VOTE_REQUEST:
                log.info("Received VoteRequest start: {}", System.currentTimeMillis());
                // 处理投票请求
                log.info("Received RequestVoteRequest: {}", request.getData());
                // 这里假设有一个方法处理投票请求
                RequestVoteResponse voteResponse = raftNode.getConsensusModule().handleRequestVote((RequestVoteRequest) request.getData());
                log.info("Received VoteRequest end: {}", System.currentTimeMillis());
                ctx.channel().writeAndFlush(RaftMessage.builder().type(request.getType()).requestId(request.getRequestId()).data(voteResponse).build());
                break;
        }
    }
}
