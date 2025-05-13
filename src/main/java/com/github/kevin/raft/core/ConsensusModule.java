package com.github.kevin.raft.core;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * 共识模块
 */
@Data
@RequiredArgsConstructor
public class ConsensusModule {

    private final RaftNode raftNode;

}
