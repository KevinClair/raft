package com.github.kevin.raft.netty.server;

import com.github.kevin.raft.core.RaftNode;
import com.github.kevin.raft.netty.common.codec.MessageDecoder;
import com.github.kevin.raft.netty.common.codec.MessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Netty服务端
 *
 * @author KevinClair
 */
public class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private Channel channel;
    private NettyServerHandler nettyServerHandler;
    // 配置服务器
    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

    /**
     * 服务端初始化
     *
     * @param port 启动端口号
     */
    public NettyServer(Integer port, RaftNode raftNode) {
        this.nettyServerHandler = new NettyServerHandler(raftNode);
        this.start(port);
    }

    /**
     * 启动 Netty Server
     */
    private void start(Integer port) {
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))
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
//                                    .addLast(new LoggingHandler(LogLevel.INFO))
                                    // 空闲检测
//                                    .addLast(new IdleStateHandler(CommonConstant.READ_TIMEOUT_SECONDS, 0, 0))
                                    // 解码器
                                    .addLast(new MessageDecoder(65535, 4, 4, -8, 0))
                                    // 编码器
                                    .addLast(new MessageEncoder())
                                    // 心跳处理器
                                    .addLast(new HeartBeatServerHandler())
                                    // 服务端处理器
                                    .addLast(nettyServerHandler);
                        }
                    });

            // 启动服务
            ChannelFuture future = bootstrap.bind().sync();
            if (future.isSuccess()) {
                logger.debug("Netty Server started successfully.");
                channel = future.channel();
                channel.closeFuture().addListener(closeFuture -> {
                            this.close();
                        }
                );
            }
        } catch (Exception e) {
            logger.error("Netty sever started failed,msg:{}", ExceptionUtils.getStackTrace(e));
        }
    }

    public void close() {
        logger.warn("Netty Server close.");
        // 关闭 Netty Server
        if (channel != null) {
            channel.close();
        }
        // 优雅关闭两个 EventLoopGroup 对象
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
