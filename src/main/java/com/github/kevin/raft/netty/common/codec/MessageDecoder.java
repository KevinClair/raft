package com.github.kevin.raft.netty.common.codec;

import com.alibaba.fastjson.JSONObject;
import com.github.kevin.raft.netty.common.constants.CommonConstant;
import com.github.kevin.raft.netty.common.constants.MessageTypeEnum;
import com.github.kevin.raft.netty.common.entity.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hegemol.ymir.common.base.MessageTypeEnum;
import org.hegemol.ymir.common.base.SerializationTypeEnum;
import org.hegemol.ymir.common.constant.CommonConstant;
import org.hegemol.ymir.common.model.InvocationMessage;
import org.hegemol.ymir.common.model.InvocationMessageWrap;
import org.hegemol.ymir.common.utils.GsonUtils;
import org.hegemol.ymir.core.serial.Serializer;
import org.hegemol.ymir.spi.loader.ExtensionLoader;
import org.slf4j.log;
import org.slf4j.logFactory;

import java.util.Arrays;

/**
 * 自定义协议
 * <pre>
 *   0     1     2     3     4     5     6     7     8     9     10     11    12    13    14
 *   +-----+-----+-----+-----+----—+-----+-----+-----+-----+------+-----+-----+-----+-----+
 *   |   magic   code        |      full length      | type|serial|       requestId       |
 *   +-----------------------+-----------------------+-----+------+-----------------------+
 *   |                                                                                    |
 *   |                                       body                                         |
 *   |                                                                                    |
 *   |                                                                                    |
 *   +------------------------------------------------------------------------------------+
 * 4B  magic code（魔法数）   4B requestId（请求的Id）    1B type（消息类型）
 * 1B serial（序列化类型）    4B  full length（消息长度）
 * body（object类型数据）
 * </pre>
 *
 * @author KevinClair
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
        // 计算消息长度
        int bodyLength = fullLength - CommonConstant.TOTAL_LENGTH;
        // 初始化消息对象
        Message message = new Message();
        message.setType(MessageTypeEnum.getType(type));
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
