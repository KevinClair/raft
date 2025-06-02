package com.github.kevin.raft.core;

import com.github.kevin.raft.common.ServerStatus;
import com.github.kevin.raft.dto.AppendEntriesRequest;
import com.github.kevin.raft.dto.AppendEntriesResponse;
import com.github.kevin.raft.dto.RequestVoteRequest;
import com.github.kevin.raft.dto.RequestVoteResponse;
import com.github.kevin.raft.entity.LogEntry;
import io.netty.util.internal.StringUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 共识模块
 */
@Data
@RequiredArgsConstructor
@Slf4j
public class ConsensusModule {

    private final RaftNode raftNode;

    // 追加日志Lock
    public final ReentrantLock appendLock = new ReentrantLock();
    // 投票Lock
    public final ReentrantLock voteLock = new ReentrantLock();

    /**
     * 处理追加日志请求
     *
     * @param appendEntriesRequest 附加日志数据
     * @return AppendEntriesResponse 响应结果
     */
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest appendEntriesRequest) {

        // 先初始化一个AppendEntriesResponse
        AppendEntriesResponse appendEntriesResponse = AppendEntriesResponse.fail();

        try {
            // 尝试加锁
            if (!appendLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                log.warn("AppendEntries lock is busy, returning failure response.");
                return appendEntriesResponse; // 返回失败响应
            }

            // 设置当前任期
            appendEntriesResponse.setTerm(raftNode.getPersistentState().getCurrentTerm());
            // 判断请求参数中的任期是否小于当前任期
            if (appendEntriesRequest.getCurrentTerm() < raftNode.getPersistentState().getCurrentTerm()) {
                log.warn("Received AppendEntries with outdated term: {}", appendEntriesRequest.getCurrentTerm());
                return appendEntriesResponse; // 返回失败响应
            }

            long currentTimeMillis = System.currentTimeMillis();
            raftNode.setPreHeartBeatTime(currentTimeMillis);
            raftNode.setPreElectionTime(currentTimeMillis);
            raftNode.setLeaderId(appendEntriesRequest.getLeaderId());

            // 如果请求的任期大于当前任期，更新当前节点状态为Follower
            if (appendEntriesRequest.getCurrentTerm() >= raftNode.getPersistentState().getCurrentTerm()) {
                log.info("Updating current term from {} to {}", raftNode.getPersistentState().getCurrentTerm(), appendEntriesRequest.getCurrentTerm());
                raftNode.setStatus(ServerStatus.FOLLOWER);
            }
            // 更新当前节点的任期
            raftNode.getPersistentState().setCurrentTerm(appendEntriesRequest.getCurrentTerm());

            // 如果追加日志请求中没有日志条目，为心跳请求
            if (CollectionUtils.isEmpty(appendEntriesRequest.getEntries())) {
                log.info("Received heartbeat from leader: {}", appendEntriesRequest.getLeaderId());
                // 处理心跳请求，更新状态机等
                // 如果leaderCommit大于当前commitIndex，则更新commitIndex
                if (appendEntriesRequest.getLeaderCommit() > raftNode.getVolatileState().getCommitIndex()) {
                    long newCommitIndex = Math.min(appendEntriesRequest.getLeaderCommit(), raftNode.getVolatileState().getLastApplied());
                    raftNode.getVolatileState().setCommitIndex(newCommitIndex);
                    raftNode.getVolatileState().setLastApplied(newCommitIndex);
                }
                // 提交之前未提交的日志条目
                long nextCommit = raftNode.getVolatileState().getCommitIndex() + 1;
                while (nextCommit <= raftNode.getVolatileState().getCommitIndex()) {
                    // 提交日志条目到状态机
                    raftNode.getStateMachine().apply(raftNode.getLogModule().read(nextCommit));
                    nextCommit++;
                }
                return AppendEntriesResponse.builder().term(raftNode.getPersistentState().getCurrentTerm()).success(true).build(); // 返回成功响应
            }
            // 处理真实的日志条目
            // 第一次
            if (raftNode.getLogModule().getLastIndex() != 0 && appendEntriesRequest.getPreviousLogIndex() != 0) {
                LogEntry logEntry = raftNode.getLogModule().read(appendEntriesRequest.getPreviousLogIndex());
                if (Objects.nonNull(logEntry)) {
                    // 如果日志在 prevLogIndex 位置处的日志条目的任期号和 prevLogTerm 不匹配，则返回 false
                    // 需要减小 nextIndex 重试.
                    if (logEntry.getTerm() != appendEntriesRequest.getPreviousLogTerm()) {
                        // 打印错误日志
                        log.error(
                                "Log term mismatch: expected {}, got {} at index {}",
                                appendEntriesRequest.getPreviousLogTerm(),
                                logEntry.getTerm(),
                                appendEntriesRequest.getPreviousLogIndex()
                        );
                        return appendEntriesResponse;
                    }
                } else {
                    // index 不对, 需要递减 nextIndex 重试.
                    return appendEntriesResponse;
                }
            }

            // 如果已经存在的日志条目和新的产生冲突（索引值相同但是任期号不同），删除这一条和之后所有的
            LogEntry existLog = raftNode.getLogModule().read(appendEntriesRequest.getPreviousLogIndex() + 1);
            if (existLog != null && existLog.getTerm() != appendEntriesRequest.getEntries().get(0).getTerm()) {
                // 删除这一条和之后所有的, 然后写入日志和状态机.
                raftNode.getLogModule().removeOnStartIndex(appendEntriesRequest.getPreviousLogIndex() + 1);
            } else if (existLog != null) {
                // 已经有日志了, 不能重复写入.
                appendEntriesResponse.setSuccess(true);
                return appendEntriesResponse;
            }

            // 写进日志
            for (LogEntry entry : appendEntriesRequest.getEntries()) {
                raftNode.getLogModule().write(entry);
                appendEntriesResponse.setSuccess(true);
            }

            //如果 leaderCommit > commitIndex，令 commitIndex 等于 leaderCommit 和 新日志条目索引值中较小的一个
            if (appendEntriesRequest.getLeaderCommit() > raftNode.getVolatileState().getCommitIndex()) {
                Long commitIndex = Math.min(appendEntriesRequest.getLeaderCommit(), raftNode.getLogModule().getLastIndex());
                raftNode.getVolatileState().setCommitIndex(commitIndex);
                raftNode.getVolatileState().setLastApplied(commitIndex);
            }

            // 下一个需要提交的日志的索引（如有）
            for (long nextCommit = raftNode.getVolatileState().getCommitIndex() + 1; nextCommit <= raftNode.getVolatileState().getCommitIndex(); nextCommit++) {
                // 提交之前的日志
                raftNode.getStateMachine().apply(raftNode.getLogModule().read(nextCommit));
            }

            appendEntriesResponse.setTerm(raftNode.getPersistentState().getCurrentTerm());
            raftNode.setStatus(ServerStatus.FOLLOWER);
            return appendEntriesResponse;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            appendLock.unlock();
        }
    }

    public RequestVoteResponse handleRequestVote(RequestVoteRequest requestVoteRequest) {
        RequestVoteResponse.RequestVoteResponseBuilder builder = RequestVoteResponse.builder();
        try {
            if (!voteLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                return builder.term(raftNode.getPersistentState().getCurrentTerm()).voteGranted(false).build();
            }

            // 对方任期没有自己新
            if (requestVoteRequest.getTerm() < raftNode.getPersistentState().getCurrentTerm()) {
                return builder.term(raftNode.getPersistentState().getCurrentTerm()).voteGranted(false).build();
            }

            // (当前节点并没有投票 或者 已经投票过了且是对方节点) && 对方日志和自己一样新
            log.info("node {} current vote for [{}], param candidateId : {}", raftNode.getAddress(), raftNode.getPersistentState().getVotedFor(), requestVoteRequest.getCandidateId());
            log.info("node {} current term {}, peer term : {}", raftNode.getAddress(), raftNode.getPersistentState().getCurrentTerm(), requestVoteRequest.getTerm());

            if ((StringUtil.isNullOrEmpty(raftNode.getPersistentState().getVotedFor()) || raftNode.getPersistentState().getVotedFor().equals(requestVoteRequest.getCandidateId()))) {

                if (raftNode.getLogModule().getLast() != null) {
                    // 对方没有自己新
                    if (raftNode.getLogModule().getLast().getTerm() > requestVoteRequest.getLastLogTerm() || raftNode.getLogModule().getLastIndex() > requestVoteRequest.getLastLogIndex()) {
                        return RequestVoteResponse.fail();
                    }
                }

                // 切换状态
                raftNode.setStatus(ServerStatus.FOLLOWER);
                // 更新
                raftNode.setLeaderId(requestVoteRequest.getCandidateId());
                raftNode.getPersistentState().setCurrentTerm(requestVoteRequest.getTerm());
                raftNode.getPersistentState().setVotedFor(requestVoteRequest.getCandidateId());
                // 返回成功
                return builder.term(raftNode.getPersistentState().getCurrentTerm()).voteGranted(true).build();
            }
            return builder.term(raftNode.getPersistentState().getCurrentTerm()).voteGranted(false).build();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            voteLock.unlock();
        }
    }
}
