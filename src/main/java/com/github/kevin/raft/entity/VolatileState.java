package com.github.kevin.raft.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 易失性状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VolatileState {

    /**
     * 已知已提交的最高的日志条目的索引
     */
    private Long commitIndex;

    /**
     * 已经被应用到状态机的最高的日志条目的索引
     */
    private Long lastApplied;

    // 领导者专用（选举后重新初始化）
    /**
     * 对于每一台服务器，发送到该服务器的下一个日志条目的索引
     */
    private Long[] nextIndex;

    /**
     * 对于每一台服务器，已知的已经复制到该服务器的最高日志条目的索引
     */
    private Long[] matchIndex;

    /**
     * 构造函数
     *
     * @param serverCount 服务器数量
     */
    public VolatileState(int serverCount) {
        this.commitIndex = 0L;
        this.lastApplied = 0L;
        this.nextIndex = new Long[serverCount];
        this.matchIndex = new Long[serverCount];
    }
}
