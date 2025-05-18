package com.github.kevin.raft.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Request<T> {

    /**
     * 请求投票
     */
    public static final int R_VOTE = 0;
    /**
     * 附加日志
     */
    public static final int A_ENTRIES = 1;

    /**
     * 请求类型
     */
    private int cmd = -1;

    private T data;
}
