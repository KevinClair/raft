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
public class RequestVoteResponse implements Serializable {

    /**
     * 当前任期号，以便于候选人去更新自己的任期
     */
    private long term;

    /**
     * 候选人赢得了此张选票时为真
     */
    private Boolean voteGranted;

    public RequestVoteResponse(Boolean voteGranted) {
        this.voteGranted = voteGranted;
    }

    public static RequestVoteResponse fail() {
        return new RequestVoteResponse(false);
    }

    public static RequestVoteResponse ok() {
        return new RequestVoteResponse(true);
    }
}
