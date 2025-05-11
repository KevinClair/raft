package com.github.kevin.raft.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志条目
 * <p>
 * 该类用于存储日志条目的相关信息
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogEntry {

    /**
     * 日志索引
     */
    private long index;

    /**
     * 日志任期
     */
    private long term;

    /**
     * 状态机执行命令
     */
    private String command;

    /**
     * 日志数据
     */
    private byte[] data;
}
