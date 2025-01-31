package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.data.SettableByName;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TestSettableByName implements SettableByName<TestSettableByName> {
    private final List<Column> columns;
    private final List<ByteBuffer> data;

    public TestSettableByName(List<Column> columns) {
        this.columns = columns;
        this.data = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            data.add(null);
        }
    }

    public ByteBuffer getData(int i) {
        return data.get(i);
    }

    public record Column(String name, DataType type) {}

    @Override
    public int firstIndexOf(String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public TestSettableByName setBytesUnsafe(int i, ByteBuffer v) {
        data.set(i, v);
        return this;
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public DataType getType(int i) {
        return columns.get(i).type;
    }

    @Override
    public CodecRegistry codecRegistry() {
        return CodecRegistry.DEFAULT;
    }

    @Override
    public ProtocolVersion protocolVersion() {
        return ProtocolVersion.DEFAULT;
    }
}
