package com.github.kevin.raft.entity;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * 日志模块，存储的是未提交的日志条目
 * <p>
 * 该类用于模拟日志的存储和操作
 * </p>
 */
@Slf4j
public class LogModule {

    // 模拟一个日志实现，简单的使用HashMap去操作
    // 实际上应该使用一个持久化的存储方式，比如文件系统或者数据库
    private Map<Long, LogEntry> logEntries;

    private ReentrantLock lock = new ReentrantLock();

    private Long LAST_INDEX_KEY = -1L;

    public LogModule() {
        this.logEntries = new HashMap<>();
    }

    public LogModule getInstance() {
        return LogModuleHolder.INSTANCE;
    }

    private static class LogModuleHolder {
        private static final LogModule INSTANCE = new LogModule();
    }

    /**
     * 获取当前最后一个日志写入的index
     *
     * @return
     */
    public Long getLastIndex() {
        return LAST_INDEX_KEY;
    }

    /**
     * 更新lastIndex
     *
     * @param index 日志索引
     */
    private void setLastIndex(Long index) {
        this.LAST_INDEX_KEY = index;
    }

    /**
     * 读取对应commitIndex的日志条目
     *
     * @param index 日志索引
     * @return
     */
    public LogEntry read(Long index) {
        return logEntries.get(index);
    }

    /**
     * 移除指定Index后的所有index条目
     *
     * @param startIndex 开始需要移除的index
     */
    public void removeOnStartIndex(Long startIndex) {
        boolean success = false;
        int count = 0;
        boolean tryLock;
        try {
            tryLock = lock.tryLock(3000, MILLISECONDS);
            if (!tryLock) {
                throw new RuntimeException("tryLock fail, removeOnStartIndex fail");
            }
            for (long i = startIndex; i <= getLastIndex(); i++) {
                logEntries.remove(i);
                ++count;
            }
            success = true;
            log.warn("LogModule removeOnStartIndex success, count={} startIndex={}, lastIndex={}", count, startIndex, getLastIndex());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (success) {
                setLastIndex(getLastIndex() - count);
            }
            lock.unlock();
        }
    }

    /**
     * 写入日志条目到日志模块
     *
     * @param logEntry
     */
    public void write(LogEntry logEntry) {

        boolean success = false;
        boolean result;
        try {
            result = lock.tryLock(3000, MILLISECONDS);
            if (!result) {
                throw new RuntimeException("write fail, tryLock fail.");
            }
            logEntry.setIndex(getLastIndex() + 1);
            logEntries.put(logEntry.getIndex(), logEntry);
            success = true;
            log.info("LogModule write success, index={}", logEntry.getIndex());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (success) {
                setLastIndex(logEntry.getIndex());
            }
            lock.unlock();
        }
    }

    /**
     * 获取最后一个日志条目
     *
     * @return
     */
    public LogEntry getLast() {
        return logEntries.get(getLastIndex());
    }
}
