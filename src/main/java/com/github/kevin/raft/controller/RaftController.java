package com.github.kevin.raft.controller;

import com.github.kevin.raft.common.ServerStatus;
import com.github.kevin.raft.core.RaftNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Raft节点控制器
 *
 * @author KevinClair
 */
@RestController
@RequestMapping("/raft")
@RequiredArgsConstructor
@Slf4j
public class RaftController {

    private final RaftNode raftNode;

    /**
     * 获取节点状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("address", raftNode.getAddress());
        result.put("status", raftNode.getStatus());
        result.put("currentTerm", raftNode.getPersistentState().getCurrentTerm());
        result.put("leaderId", raftNode.getLeaderId());
        result.put("commitIndex", raftNode.getVolatileState().getCommitIndex());
        result.put("lastApplied", raftNode.getVolatileState().getLastApplied());
        result.put("lastLogIndex", raftNode.getLogModule().getLastIndex());
        return result;
    }

    /**
     * 提交日志
     */
    @PostMapping("/log")
    public Map<String, Object> appendLog(@RequestParam String command, @RequestParam(required = false) String data) {
        Map<String, Object> result = new HashMap<>();
        
        if (raftNode.getStatus() != ServerStatus.LEADER) {
            result.put("success", false);
            result.put("message", "Not a leader, current leader: " + raftNode.getLeaderId());
            result.put("leaderId", raftNode.getLeaderId());
            return result;
        }

        boolean success = raftNode.appendLog(command, data != null ? data.getBytes() : null);
        result.put("success", success);
        result.put("message", success ? "Log appended successfully" : "Failed to append log");
        result.put("lastLogIndex", raftNode.getLogModule().getLastIndex());
        return result;
    }

    /**
     * 获取日志信息
     */
    @GetMapping("/log/{index}")
    public Map<String, Object> getLog(@PathVariable Long index) {
        Map<String, Object> result = new HashMap<>();
        com.github.kevin.raft.entity.LogEntry logEntry = raftNode.getLogModule().read(index);
        
        if (logEntry == null) {
            result.put("success", false);
            result.put("message", "Log entry not found");
            return result;
        }

        result.put("success", true);
        result.put("index", logEntry.getIndex());
        result.put("term", logEntry.getTerm());
        result.put("command", logEntry.getCommand());
        result.put("data", logEntry.getData() != null ? new String(logEntry.getData()) : null);
        return result;
    }
}
