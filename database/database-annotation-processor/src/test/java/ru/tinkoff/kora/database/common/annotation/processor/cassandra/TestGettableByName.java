package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.data.GettableByName;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;

import java.nio.ByteBuffer;
import java.util.List;

public class TestGettableByName implements GettableByName {
    public TestGettableByName(List<Value<?>> values) {
        this.values = values;
    }

    public record Value<T>(TypeCodec<T> codec, String column, T value) {
        ByteBuffer encode() {
            return codec.encode(value, ProtocolVersion.DEFAULT);
        }
    }

    private final List<Value<?>> values;

    @Override
    public int firstIndexOf(String name) {
        for (int i = 0; i < values.size(); i++) {
            var value = values.get(i);
            if (value.column.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public DataType getType(String name) {
        return values.get(firstIndexOf(name)).codec.getCqlType();
    }

    @Override
    public ByteBuffer getBytesUnsafe(int i) {
        return values.get(i).encode();
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public DataType getType(int i) {
        return values.get(i).codec.getCqlType();
    }

    @Override
    public CodecRegistry codecRegistry() {
        return CodecRegistry.DEFAULT;
    }

    @Override
    public ProtocolVersion protocolVersion() {
        return ProtocolVersion.DEFAULT;
    }

    @Override
    public UdtValue getUdtValue(String name) {
        return GettableByName.super.getUdtValue(name);
    }
}
