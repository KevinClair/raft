package com.github.kevin.raft.core.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(value = RaftConfigurationProperties.class)
public class RaftAutoConfiguration {

}
