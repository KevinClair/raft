package com.github.kevin.raft.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raft")
@Data
public class RaftConfigurationProperties {

    /**
     * 本机Ip
     */
    private String selfAddress;

    /**
     * 启动端口
     */
    private Integer port;

    /**
     * 除本机之外的Ip地址
     */
    private String address = "localhost:27015,localhost:27016,localhost:27017,localhost:27018,localhost:27019";
}
