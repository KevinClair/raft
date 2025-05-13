package com.github.kevin.raft.netty.client;

import com.github.kevin.raft.core.RaftRpcClient;
import com.github.kevin.raft.core.RaftRpcClientContainer;
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
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.concurrent.TimeUnit;

/**
 * Netty客户端
 *
 * @author KevinClair
 **/
@Slf4j
@Data
public class NettyClient {

    private EventLoopGroup loopGroup = new NioEventLoopGroup(4);

    private Channel channel;

    private final Bootstrap bootstrap = new Bootstrap();

    /**
     * 客户端初始化
     */
    public NettyClient() {
        this.startClient();
    }

    private void startClient() {
        // 配置客户端
        bootstrap.group(loopGroup)
                .channel(NioSocketChannel.class)
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
                                .addLast(new NettyClientHandler());
                    }
                });
    }

    public void connect(String address) {
        if (RaftRpcClientContainer.getInstance().contains(address)) {
            return;
        }
        String[] addrInfo = address.split(":");
        String host = addrInfo[0];
        String port = addrInfo[1];

        try {
            bootstrap.connect(host, Integer.parseInt(port)).sync().addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    log.error("Netty client connect to server failed, address:{}", address);
                    this.reconnect(address);
                    return;
                }
                log.info("Netty client connect to server successfully, address:{}", address);
                channel = future.channel();
                RaftRpcClientContainer.getInstance().addRpcClient(address, new RaftRpcClient(channel));
                channel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
                    this.close();
                });
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
     */
    public void reconnect(String address) {
        loopGroup.schedule(() -> {
            if (RaftRpcClientContainer.getInstance().contains(address)) {
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug("Netty client start reconnect, address:{}", address);
            }
            this.connect(address);
        }, CommonConstant.RECONNECT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 关闭
     */
    private void close() {
        if (loopGroup != null) {
            log.warn("Netty client close loop group.");
            loopGroup.shutdownGracefully();
        }
    }
}
