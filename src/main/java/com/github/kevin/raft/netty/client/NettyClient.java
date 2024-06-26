package com.github.kevin.raft.netty.client;

import com.github.kevin.raft.netty.common.codec.MessageDecoder;
import com.github.kevin.raft.netty.common.codec.MessageEncoder;
import com.github.kevin.raft.netty.common.constants.CommonConstant;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.DisposableBean;

import java.util.concurrent.TimeUnit;

/**
 * Netty客户端
 *
 * @author KevinClair
 **/
@Slf4j
public class NettyClient implements DisposableBean {

    private EventLoopGroup loopGroup = new NioEventLoopGroup(4);

    private Channel channel;

    /**
     * 客户端初始化
     *
     * @param address 服务端地址
     */
    public void initClient(String address) {
        NettyClientHandler handler = new NettyClientHandler();
        this.startClient(address, handler);
    }

    private void startClient(String address, NettyClientHandler handler) {
        String[] addrInfo = address.split(":");
        final String serverAddress = addrInfo[0];
        final String serverPort = addrInfo[1];
        // 配置客户端
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(loopGroup)
                .channel(NioSocketChannel.class)
                .remoteAddress(serverAddress, Integer.parseInt(serverPort))
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline
                                /*Netty提供的日志打印Handler，可以展示发送接收出去的字节*/
                                .addLast(new LoggingHandler(LogLevel.INFO))
                                // 空闲检测
                                .addLast(new IdleStateHandler(0, CommonConstant.WRITE_TIMEOUT_SECONDS, 0))
                                // 解码器
                                .addLast(new MessageDecoder(65535, 4, 4, -8, 0))
                                // 编码器
                                .addLast(new MessageEncoder())
                                // 心跳检测
//                                .addLast(new HeartBeatClientHandler())
                                // 客户端业务处理器
                                .addLast(handler);
                    }
                });
        // 启用客户端连接
        try {
            ChannelFuture future = bootstrap.connect().sync().addListener((ChannelFutureListener) channelFuture -> {
                if (!channelFuture.isSuccess()) {
                    this.reconnect(address, handler);
                    return;
                }
                log.info("Server address:{} connect successfully.", address);
                channel = channelFuture.channel();
            });
        } catch (InterruptedException e) {
            log.error("Netty client start error:{}", ExceptionUtils.getStackTrace(e));
            this.close();
        }
    }

    /**
     * 重新链接服务端
     *
     * @param address 客户端地址
     * @param handler 处理器
     */
    public void reconnect(String address, NettyClientHandler handler) {
        loopGroup.schedule(() -> {
            if (log.isDebugEnabled()) {
                log.debug("Netty client start reconnect, address:{}", address);
            }
            this.startClient(address, handler);
        }, CommonConstant.RECONNECT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() throws Exception {
        this.close();
    }

    /**
     * 关闭
     */
    private void close() {
        if (loopGroup != null) {
            loopGroup.shutdownGracefully();
        }
    }
}
