package com.github.kevin.raft.netty.common.entity;

import com.github.kevin.raft.netty.common.constants.MessageTypeEnum;
import lombok.Data;

@Data
public class Message<T> {

    /**
     * 请求id
     */
    private int requestId;

    /**
     * 本次消息类型 {@link MessageTypeEnum}
     */
    private MessageTypeEnum type;

    /**
     * 数据
     */
    private T data;
}
