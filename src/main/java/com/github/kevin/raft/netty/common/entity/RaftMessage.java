package com.github.kevin.raft.netty.common.entity;

import com.github.kevin.raft.netty.common.constants.MessageTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomUtils;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RaftMessage<T> implements Serializable {

    /**
     * 请求id
     */
    private int requestId = RandomUtils.nextInt();

    /**
     * 本次消息类型 {@link com.github.kevin.raft.netty.common.constants.MessageTypeEnum}
     */
    private MessageTypeEnum type;

    /**
     * 数据
     */
    private T data;

    public RaftMessage(MessageTypeEnum type, T data) {
        this.type = type;
        this.data = data;
    }
}
