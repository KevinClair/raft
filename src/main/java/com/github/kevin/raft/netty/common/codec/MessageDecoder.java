package com.github.kevin.raft.netty.common.codec;

import com.alibaba.fastjson.JSONObject;
import com.github.kevin.raft.netty.common.constants.CommonConstant;
import com.github.kevin.raft.netty.common.constants.MessageTypeEnum;
import com.github.kevin.raft.netty.common.entity.RaftMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.Arrays;

/**
 * 消息编码器，按照协议类型写入数据
 * <p>
 * 0     1     2     3     4     5     6     7     8     9     10     11    12    13
 * +-----+-----+-----+-----+----—+-----+-----+-----+-----+------+-----+-----+-----+
 * |   magic   code        |      full length      | type|        requestId       |
 * +-----------------------+-----------------------+-----+------+-----------------+
 * |                                                                              |
 * |                                       body                                   |
 * |                                                                              |
 * |                                                                              |
 * +------------------------------------------------------------------------------+
 * 4B  magic code（魔法数）   4B full length（请求的Id）    1B type（消息类型）
 * 4B  requestId（消息长度）   body（object类型数据）
 */
@Slf4j
public class MessageDecoder extends LengthFieldBasedFrameDecoder {

    public MessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        Object decode = super.decode(ctx, in);
        if (decode instanceof ByteBuf){
            ByteBuf byteBuf = (ByteBuf) decode;
            // 判断可读长度
            if (byteBuf.readableBytes() >= CommonConstant.TOTAL_LENGTH){
                try {
                    return decodeByteBuf(ctx, byteBuf);
                } catch (Exception e) {
                    log.error("Decode message error:{}", ExceptionUtils.getStackTrace(e));
                } finally {
                    // 释放
                    byteBuf.release();
                }
            }
        }
        return decode;
    }

    private Object decodeByteBuf(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        // 校验魔法值是否正确
        checkMagicNumber(ctx, byteBuf);
        // 读取消息总长度
        int fullLength = byteBuf.readInt();
        // 读取消息类型
        byte type = byteBuf.readByte();
        // 读取消息id
        int requestId = byteBuf.readInt();
        // 计算消息长度
        int bodyLength = fullLength - CommonConstant.TOTAL_LENGTH;
        // 初始化消息对象
        RaftMessage message = new RaftMessage();
        message.setType(MessageTypeEnum.getType(type));
        message.setRequestId(requestId);
        // 如果不是心跳类型，此处应该都是大于0；心跳类型的请求不包含请求体，所以一般fullLength就是全部的消息长度
        if (bodyLength > 0) {
            byte[] body = new byte[bodyLength];
            byteBuf.readBytes(body);
            message.setData(Serializer.deserialize(body));
        }
        if (log.isDebugEnabled()) {
            log.debug("Channel {} decoder message success, message content:{}", JSONObject.toJSONString(message));
        }
        return message;
    }

    private void checkMagicNumber(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        // 读取魔法值
        int magicNumberLength = CommonConstant.MAGIC_NUMBER.length;
        byte[] magicNums = new byte[magicNumberLength];
        byteBuf.readBytes(magicNums);
        for (int i = 0; i < magicNumberLength; i++) {
            if (magicNums[i] != CommonConstant.MAGIC_NUMBER[i]) {
                ctx.channel().close();
                throw new IllegalArgumentException(String.format("Invalid magic code: %s", Arrays.toString(magicNums)));
            }
        }
    }
}
