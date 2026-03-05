package com.github.kevin.raft.core;

import com.github.kevin.raft.common.ServerStatus;
import com.github.kevin.raft.dto.AppendEntriesRequest;
import com.github.kevin.raft.dto.AppendEntriesResponse;
import com.github.kevin.raft.dto.RequestVoteRequest;
import com.github.kevin.raft.dto.RequestVoteResponse;
import com.github.kevin.raft.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Raft共识模块测试
 * 这些测试直接测试ConsensusModule的逻辑，不需要启动完整的Raft节点
 */
class ConsensusModuleTest {

    private RaftNodeMock raftNodeMock;
    private ConsensusModule consensusModule;

    /**
     * 模拟的RaftNode，用于测试
     */
    static class RaftNodeMock extends RaftNode {
        private ServerStatus status = ServerStatus.FOLLOWER;
        private String leaderId;
        private PersistentState persistentState;
        private VolatileState volatileState;
        private LogModule logModule;
        private StateMachine stateMachine;
        private long preHeartBeatTime = 0;
        private long preElectionTime = 0;

        public RaftNodeMock() {
            super("mock-node", new ArrayList<>(), 0, false);
            this.persistentState = new PersistentState();
            this.volatileState = new VolatileState(3);
            this.logModule = new LogModule();
            this.stateMachine = new StateMachine();
        }

        @Override
        public ServerStatus getStatus() {
            return status;
        }

        @Override
        public void setStatus(ServerStatus status) {
            this.status = status;
        }

        @Override
        public String getLeaderId() {
            return leaderId;
        }

        @Override
        public void setLeaderId(String leaderId) {
            this.leaderId = leaderId;
        }

        @Override
        public PersistentState getPersistentState() {
            return persistentState;
        }

        @Override
        public VolatileState getVolatileState() {
            return volatileState;
        }

        @Override
        public LogModule getLogModule() {
            return logModule;
        }

        @Override
        public StateMachine getStateMachine() {
            return stateMachine;
        }

        @Override
        public void setPreHeartBeatTime(long time) {
            this.preHeartBeatTime = time;
        }

        @Override
        public void setPreElectionTime(long time) {
            this.preElectionTime = time;
        }
    }

    @BeforeEach
    void setUp() {
        raftNodeMock = new RaftNodeMock();
        consensusModule = new ConsensusModule(raftNodeMock);
    }

    @Test
    void testHandleRequestVote_GrantVote() {
        // 准备请求投票的请求
        RequestVoteRequest request = RequestVoteRequest.builder()
                .term(1L)
                .candidateId("127.0.0.1:27016")
                .lastLogIndex(0L)
                .lastLogTerm(0L)
                .build();

        // 处理投票请求
        RequestVoteResponse response = consensusModule.handleRequestVote(request);

        // 验证结果
        assertTrue(response.getVoteGranted(), "应该授予投票");
        assertEquals(1L, response.getTerm(), "任期应该更新");
        assertEquals("127.0.0.1:27016", raftNodeMock.getPersistentState().getVotedFor(), "应该记录投票对象");
    }

    @Test
    void testHandleRequestVote_RejectLowerTerm() {
        // 先设置当前任期为2
        raftNodeMock.getPersistentState().setCurrentTerm(2L);

        // 准备一个任期为1的投票请求
        RequestVoteRequest request = RequestVoteRequest.builder()
                .term(1L)
                .candidateId("127.0.0.1:27016")
                .lastLogIndex(0L)
                .lastLogTerm(0L)
                .build();

        // 处理投票请求
        RequestVoteResponse response = consensusModule.handleRequestVote(request);

        // 验证结果
        assertFalse(response.getVoteGranted(), "不应该授予投票（任期过低）");
        assertEquals(2L, response.getTerm(), "应该返回当前任期");
    }

    @Test
    void testHandleRequestVote_AlreadyVoted() {
        // 先投票给另一个候选人
        raftNodeMock.getPersistentState().setCurrentTerm(1L);
        raftNodeMock.getPersistentState().setVotedFor("127.0.0.1:27017");

        // 准备投票请求
        RequestVoteRequest request = RequestVoteRequest.builder()
                .term(1L)
                .candidateId("127.0.0.1:27016")
                .lastLogIndex(0L)
                .lastLogTerm(0L)
                .build();

        // 处理投票请求
        RequestVoteResponse response = consensusModule.handleRequestVote(request);

        // 验证结果
        assertFalse(response.getVoteGranted(), "不应该授予投票（已经投给其他候选人）");
    }

    @Test
    void testHandleAppendEntries_Heartbeat() {
        // 准备心跳请求
        AppendEntriesRequest request = AppendEntriesRequest.builder()
                .currentTerm(1L)
                .leaderId("127.0.0.1:27016")
                .previousLogIndex(0L)
                .previousLogTerm(0L)
                .entries(null) // 空表示心跳
                .leaderCommit(0L)
                .build();

        // 处理心跳
        AppendEntriesResponse response = consensusModule.handleAppendEntries(request);

        // 验证结果
        assertTrue(response.isSuccess(), "心跳应该成功");
        assertEquals(1L, response.getTerm(), "任期应该更新");
        assertEquals(ServerStatus.FOLLOWER, raftNodeMock.getStatus(), "节点应该保持follower状态");
        assertEquals("127.0.0.1:27016", raftNodeMock.getLeaderId(), "应该记录leader地址");
    }

    @Test
    void testHandleAppendEntries_RejectLowerTerm() {
        // 先设置当前任期为2
        raftNodeMock.getPersistentState().setCurrentTerm(2L);

        // 准备一个任期为1的AppendEntries请求
        AppendEntriesRequest request = AppendEntriesRequest.builder()
                .currentTerm(1L)
                .leaderId("127.0.0.1:27016")
                .previousLogIndex(0L)
                .previousLogTerm(0L)
                .entries(null)
                .leaderCommit(0L)
                .build();

        // 处理请求
        AppendEntriesResponse response = consensusModule.handleAppendEntries(request);

        // 验证结果
        assertFalse(response.isSuccess(), "应该拒绝（任期过低）");
        assertEquals(2L, response.getTerm(), "应该返回当前任期");
    }

    @Test
    void testHandleAppendEntries_WithLogEntries() {
        // 设置当前任期
        raftNodeMock.getPersistentState().setCurrentTerm(1L);

        // 准备日志条目
        List<LogEntry> entries = new ArrayList<>();
        LogEntry entry = new LogEntry(1L, 1L, "SET_KEY", "value".getBytes());
        entries.add(entry);

        // 准备AppendEntries请求
        AppendEntriesRequest request = AppendEntriesRequest.builder()
                .currentTerm(1L)
                .leaderId("127.0.0.1:27016")
                .previousLogIndex(0L)
                .previousLogTerm(0L)
                .entries(entries)
                .leaderCommit(0L)
                .build();

        // 处理请求
        AppendEntriesResponse response = consensusModule.handleAppendEntries(request);

        // 验证结果
        assertTrue(response.isSuccess(), "日志复制应该成功");
        assertEquals(1L, raftNodeMock.getLogModule().getLastIndex(), "日志索引应该更新");
        assertNotNull(raftNodeMock.getLogModule().read(1L), "日志条目应该被写入");
    }

    @Test
    void testHandleAppendEntries_LogConflict() {
        // 先写入一个日志条目
        LogEntry existingEntry = new LogEntry(1L, 1L, "OLD_COMMAND", "old_data".getBytes());
        raftNodeMock.getLogModule().write(existingEntry);
        
        raftNodeMock.getPersistentState().setCurrentTerm(2L);

        // 准备一个冲突的日志条目（相同索引，不同任期）
        List<LogEntry> entries = new ArrayList<>();
        LogEntry conflictEntry = new LogEntry(1L, 2L, "NEW_COMMAND", "new_data".getBytes());
        entries.add(conflictEntry);

        // 准备AppendEntries请求
        AppendEntriesRequest request = AppendEntriesRequest.builder()
                .currentTerm(2L)
                .leaderId("127.0.0.1:27016")
                .previousLogIndex(0L)
                .previousLogTerm(0L)
                .entries(entries)
                .leaderCommit(0L)
                .build();

        // 处理请求
        AppendEntriesResponse response = consensusModule.handleAppendEntries(request);

        // 验证结果
        assertTrue(response.isSuccess(), "应该成功处理冲突");
        LogEntry savedEntry = raftNodeMock.getLogModule().read(1L);
        assertNotNull(savedEntry, "日志应该被更新");
        assertEquals(2L, savedEntry.getTerm(), "日志任期应该被更新");
    }
}
