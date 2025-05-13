package com.github.kevin.raft.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 持久化状态
 * <p>
 * 该类用于存储持久化状态的相关信息
 * </p>
 */
@Data
@AllArgsConstructor
public class PersistentState {
    /**
     * 当前任期
     */
    private long currentTerm;

    /**
     * 投票的候选人地址
     */
    private String votedFor;

    /**
     * 日志条目列表
     */
    private List<LogEntry> log;

    public PersistentState() {
        this.currentTerm = 0;
        this.votedFor = null;
        this.log = new ArrayList<>();
        // 初始添加一个空日志条目，索引为0
        this.log.add(new LogEntry(0, 0, "", null));
    }

    /**
     * 获取当前日志条目的索引
     *
     * @return 当前日志条目的索引
     */
    public long getLastLogIndex() {
        return log.size() > 0 ? log.get(log.size() - 1).getIndex() : 0;
    }

    /**
     * 获取当前日志条目的任期
     *
     * @return 当前日志条目的任期
     */
    public long getLastLogTerm() {
        return log.size() > 0 ? log.get(log.size() - 1).getTerm() : 0;
    }
}
