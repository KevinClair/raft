package com.github.kevin.raft.netty.common.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 请求类型
 */
@RequiredArgsConstructor
@Getter
public enum MessageTypeEnum {
    // 请求投票
    VOTE_REQUEST((byte) 0x01),

    // 追加日志
    APPEND_ENTRIES_REQUEST((byte) 0x02);

    private final byte code;

    /**
     * 根据code查询消息类型
     *
     * @param code code
     * @return 消息类型
     */
    public static MessageTypeEnum getType(byte code) {
        for (MessageTypeEnum each : MessageTypeEnum.values()) {
            if (each.getCode() == code) {
                return each;
            }
        }
        return null;
    }
}
