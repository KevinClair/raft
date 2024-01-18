package com.github.kevin.raft.netty.common.constants;

/**
 * 常量
 */
public class CommonConstant {

    /**
     * 请求写超时时间
     */
    public static final Integer WRITE_TIMEOUT_SECONDS = 30;

    /**
     * 魔法值，协议中定义
     */
    public static final byte[] MAGIC_NUMBER = {(byte) 'r', (byte) 'a', (byte) 'f', (byte) 't'};

    /**
     * 消息体的长度
     */
    public static final byte TOTAL_LENGTH = 9;
}
