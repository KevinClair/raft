package com.github.kevin.raft.core;

import com.github.kevin.raft.netty.common.entity.RaftMessage;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RequestFutureManager {

    /**
     * 存储request的返回信息，key为每次请求{@link RaftMessage}的requestId，value为{@link CompletableFuture <Response<?>> }
     */
    private static final Map<Integer, CompletableFuture<RaftMessage>> requestMap = new ConcurrentHashMap<>();

    /**
     * 完成任务
     *
     * @param data 返回数据
     */
    public static void completeTask(RaftMessage data) {
        CompletableFuture<RaftMessage> responseFuture = requestMap.get(data.getRequestId());
        // 如果超时导致requestMap中没有保存值，此处会返回null的future，直接操作会导致NullPointException.
        if (Objects.isNull(responseFuture)) {
            return;
        }
        responseFuture.complete(data);
    }

    /**
     * 添加任务
     *
     * @param requestId 请求id
     * @param future    future对象
     */
    public static void putTask(Integer requestId, CompletableFuture<RaftMessage> future) {
        requestMap.put(requestId, future);
    }

    /**
     * 删除任务
     *
     * @param requestId 请求id
     */
    public static void remove(Integer requestId) {
        requestMap.remove(requestId);
    }
}
