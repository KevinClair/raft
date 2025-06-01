package com.github.kevin.raft.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RequestVoteRequest implements Serializable {

    // 当前任期
    private Long term;

    // 被请求者id(ip:port)
    private String serverId;

    // 候选人id(ip:port)
    private String candidateId;

    // 候选人最后日志的任期
    private Long lastLogTerm;

    // 候选人最后日志的索引
    private Long lastLogIndex;
}
