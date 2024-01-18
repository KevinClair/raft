package com.github.kevin.raft.netty.client;

import com.github.kevin.raft.netty.common.entity.Message;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端channel管理器
 */
@ChannelHandler.Sharable
@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<Message<String>> {


    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Message<String> data) throws Exception {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

    }
}
