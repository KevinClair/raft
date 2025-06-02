package com.github.kevin.raft.netty.common.codec;

import com.alibaba.fastjson.JSONObject;
import com.github.kevin.raft.netty.common.constants.CommonConstant;
import com.github.kevin.raft.netty.common.entity.RaftMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

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
public class MessageEncoder extends MessageToByteEncoder<RaftMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RaftMessage object, ByteBuf byteBuf) throws Exception {
        // 魔法值
        byteBuf.writeBytes(CommonConstant.MAGIC_NUMBER);
        // 标记当前的写位置
        byteBuf.markWriterIndex();
        // 预留空间写入长度
        byteBuf.writerIndex(byteBuf.writerIndex() + 4);
        // 消息类型
        byteBuf.writeByte(object.getType().getCode());
        // 请求id
        byteBuf.writeInt(object.getRequestId());
        int fullLength = CommonConstant.TOTAL_LENGTH;
        // 如果不是心跳类型的请求，计算Body长度
        byte[] body = Serializer.serialize(object.getData());
        fullLength += body.length;
        // 写入最终的消息
        if (Objects.nonNull(body)) {
            byteBuf.writeBytes(body);
        }
        // 计算写入长度的位置
        int writerIndex = byteBuf.writerIndex();
        byteBuf.resetWriterIndex();
        byteBuf.writeInt(fullLength);
        byteBuf.writerIndex(writerIndex);
        // 回到写节点
        if (log.isDebugEnabled()) {
            log.debug("Channel {} encoder message success, message content:{}", channelHandlerContext.channel().id(), JSONObject.toJSONString(object));
        }
    }
}
