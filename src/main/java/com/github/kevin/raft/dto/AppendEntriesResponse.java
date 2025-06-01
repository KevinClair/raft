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
public class AppendEntriesResponse implements Serializable {

    /**
     * 当前的任期号，用于领导人去更新自己
     */
    private Long term;

    /**
     * 跟随者包含了匹配上 prevLogIndex 和 prevLogTerm 的日志时为真
     */
    private boolean success;

    public static AppendEntriesResponse fail() {
        return new AppendEntriesResponse(false);
    }

    public static AppendEntriesResponse ok() {
        return new AppendEntriesResponse(true);
    }

    private AppendEntriesResponse(boolean success) {
        this.success = success;
    }

}
