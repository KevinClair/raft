package com.github.kevin.raft.core.configuration;

import com.github.kevin.raft.netty.server.NettyServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RaftAutoConfiguration {

    /**
     * 初始化服务端
     *
     * @param properties
     * @return
     */
    @Bean
    public NettyServer nettyServer(RaftConfigurationProperties properties) {
        return new NettyServer(properties);
    }
}
