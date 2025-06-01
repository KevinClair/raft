package com.github.kevin.raft.entity;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class StateMachine {

    private final Map<String, LogEntry> stateMachineMap;

    public StateMachine() {
        this.stateMachineMap = new HashMap<>();
    }

    private static class StateMachineHolder {
        private static final StateMachine INSTANCE = new StateMachine();
    }

    public static StateMachine getInstance() {
        return StateMachineHolder.INSTANCE;
    }

    /**
     * 写入数据
     *
     * @param logEntry
     */
    public void apply(LogEntry logEntry) {
        if (StringUtils.hasText(logEntry.getCommand())) {
            stateMachineMap.put(logEntry.getCommand(), logEntry);
        }
    }
}
