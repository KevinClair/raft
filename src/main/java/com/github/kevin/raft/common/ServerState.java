package com.github.kevin.raft.common;

/**
 * 服务器状态
 * <p>
 * 该类用于存储服务器的状态
 * </p>
 */
public enum ServerState {

    FOLLOWER,

    CANDIDATE,

    LEADER;
}
