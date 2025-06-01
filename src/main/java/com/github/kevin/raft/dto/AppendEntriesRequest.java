package com.github.kevin.raft.dto;

import com.github.kevin.raft.entity.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppendEntriesRequest implements Serializable {

    /**
     * 当前任期
     */
    private Long currentTerm;

    /**
     * leader的address
     */
    private String leaderId;

    /**
     * 新的日志条目紧随之前的索引值
     */
    private Long previousLogIndex;

    /**
     * prevLogIndex 条目的任期号
     */
    private Long previousLogTerm;

    /**
     * 准备存储的日志条目（表示心跳时为空；一次性发送多个是为了提高效率）
     */
    private List<LogEntry> entries;

    /**
     * 领导人已经提交的日志的索引值
     */
    private Long leaderCommit;
}
