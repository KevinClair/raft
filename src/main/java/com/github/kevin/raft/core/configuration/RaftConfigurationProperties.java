package com.github.kevin.raft.core.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "raft")
@Data
public class RaftConfigurationProperties {

    private List<String> address = Arrays.asList("localhost:8080", "localhost:8081", "localhost:8082", "localhost:8083", "localhost:8084");
}
