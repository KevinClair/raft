package com.github.kevin.raft.netty.server;

import com.github.kevin.raft.core.configuration.RaftConfigurationProperties;
import com.github.kevin.raft.netty.common.codec.MessageDecoder;
import com.github.kevin.raft.netty.common.codec.MessageEncoder;
import com.github.kevin.raft.netty.common.constants.CommonConstant;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;

/**
 * Netty服务端
 *
 * @author KevinClair
 */
@RequiredArgsConstructor
public class NettyServer implements DisposableBean, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private Channel channel;
    private NettyServerHandler nettyServerHandler = new NettyServerHandler();
    // 配置服务器
    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

    private final RaftConfigurationProperties properties;

    @Override
    public void destroy() {
        // 关闭 Netty Server
        if (channel != null) {
            channel.close();
        }
        // 优雅关闭两个 EventLoopGroup 对象
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(properties.getPort()))
                    // 表示系统用于临时存放已完成三次握手的请求的队列的最大长度
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    // 是否开启TCP底层的心跳机制
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            // 获得 Channel 对应的 ChannelPipeline
                            ChannelPipeline channelPipeline = channel.pipeline();
                            // 添加一堆 NettyServerHandler 到 ChannelPipeline 中
                            channelPipeline
                                    /*Netty提供的日志打印Handler，可以展示发送接收出去的字节*/
                                    .addLast(new LoggingHandler(LogLevel.INFO))
                                    // 空闲检测
                                    .addLast(new IdleStateHandler(CommonConstant.READ_TIMEOUT_SECONDS, 0, 0))
                                    // 解码器
                                    .addLast(new MessageDecoder(65535, 4, 4, -8, 0))
                                    // 编码器
                                    .addLast(new MessageEncoder())
                                    // 心跳处理器
//                                    .addLast(new HeartBeatServerHandler())
                                    // 服务端处理器
                                    .addLast(nettyServerHandler);
                        }
                    });

            // 启动服务
            ChannelFuture future = bootstrap.bind().sync();
            if (future.isSuccess()) {
                logger.debug("Netty Server started successfully.");
                channel = future.channel();
            }
        } catch (Exception e) {
            logger.error("Netty sever started failed,msg:{}", ExceptionUtils.getStackTrace(e));
        }
    }
}
