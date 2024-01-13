package com.github.kevin.raft.netty.common.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 请求类型
 */
@RequiredArgsConstructor
@Getter
public enum MessageTypeEnum {
    // 心跳请求
    HEART_BEAT_RQEUEST((byte) 0x01),

    // 心跳响应
    HEART_BEAT_RESPONSE((byte) 0x02),

    // 服务接口请求
    SERVICE_REQUEST((byte) 0x03),

    // 服务接口响应
    SERVICE_RESPONSE((byte) 0x04);

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
