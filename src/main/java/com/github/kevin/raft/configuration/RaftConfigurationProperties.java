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
    private String address = "127.0.0.1:27015,127.0.0.1:27016,127.0.0.1:27017,127.0.0.1:27018,127.0.0.1:27019";

    /**
     * 开启选举
     */
    private Boolean startElection = true;
}
