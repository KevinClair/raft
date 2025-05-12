package com.github.kevin.raft.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "raft")
@Data
public class RaftConfigurationProperties {

    private List<String> address = Arrays.asList("localhost:27015", "localhost:27016", "localhost:27017", "localhost:27018", "localhost:27019");

    private Integer port;
}
