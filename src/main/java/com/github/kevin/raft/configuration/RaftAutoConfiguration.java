package com.github.kevin.raft.configuration;

import com.github.kevin.raft.core.RaftNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class RaftAutoConfiguration {

    @Bean
    public RaftNode raftNode(RaftConfigurationProperties properties) {
        return new RaftNode(properties.getAddress(), Arrays.asList(properties.getAddress().split(",")), properties.getPort());
    }
}
