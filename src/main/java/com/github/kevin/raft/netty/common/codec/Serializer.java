package com.github.kevin.raft.netty.common.codec;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.github.kevin.raft.netty.common.entity.Request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * 序列化和反序列化
 * todo protobuf
 */
public class Serializer {

    public static byte[] serialize(final Object object) throws Exception {
        Hessian2Output hessianOutput = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();) {
            hessianOutput = new Hessian2Output(outputStream);
            hessianOutput.writeObject(object);
            hessianOutput.flush();
            return outputStream.toByteArray();
        } finally {
            if (Objects.nonNull(hessianOutput)) {
                hessianOutput.close();
            }
        }
    }

    public static Request deserialize(final byte[] data) throws Exception {
        Hessian2Input hessian2Input = null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);) {
            hessian2Input = new Hessian2Input(inputStream);
            return (Request) hessian2Input.readObject();
        } finally {
            if (Objects.nonNull(hessian2Input)){
                hessian2Input.close();
            }
        }
    }
}
