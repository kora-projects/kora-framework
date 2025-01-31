package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.internal.core.type.UserDefinedTypeBuilder;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraUdtAnnotationProcessor;
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper;
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowColumnMapper;
import ru.tinkoff.kora.database.common.annotation.processor.AbstractExtensionTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class CassandraUdtTest extends AbstractExtensionTest {
    private final UserDefinedType nestedUdtType = new UserDefinedTypeBuilder("test", "test_tested")
        .withField("f1", DataTypes.TEXT)
        .build();
    private final UserDefinedType udtType = new UserDefinedTypeBuilder("test", "test")
        .withField("f1", DataTypes.TEXT)
        .withField("f2", DataTypes.INT)
        .withField("f3", nestedUdtType)
        .build();

    private UdtValue randomValue() {
        var nestedValue = nestedUdtType.newValue(UUID.randomUUID().toString());
        return udtType.newValue(UUID.randomUUID().toString(), ThreadLocalRandom.current().nextInt(), nestedValue);
    }

    @Test
    public void testUdtRowColumnMapper() {
        var graph = compile(
            ParameterizedTypeName.get(ClassName.get(CassandraRowColumnMapper.class), ClassName.get(testPackage(), "TestRecord")),
            List.of(),
            List.of(new CassandraUdtAnnotationProcessor()),
            """
                @ru.tinkoff.kora.database.cassandra.annotation.UDT
                public record TestRecord(String f1, int f2, Nested f3) {
                    @ru.tinkoff.kora.database.cassandra.annotation.UDT
                    public record Nested(String f1){}
                }
                """
        );

        var mapper = (CassandraRowColumnMapper<?>) graph.get(graph.draw().getNodes().get(1));
        var udtValue = randomValue();
        var row = new TestGettableByName(List.of(new TestGettableByName.Value<>(TypeCodecs.udtOf(udtType), "column", udtValue)));
        var parsedUdt = mapper.apply(row, 0);

        assertThat(parsedUdt)
            .hasFieldOrPropertyWithValue("f1", udtValue.getString(0))
            .hasFieldOrPropertyWithValue("f2", udtValue.getInt(1))
            .extracting("f3")
            .hasFieldOrPropertyWithValue("f1", udtValue.getUdtValue(2).getString(0))
        ;
    }

    @Test
    public void testListUdtRowColumnMapper() {
        var graph = compile(
            ParameterizedTypeName.get(ClassName.get(CassandraRowColumnMapper.class), ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(testPackage(), "TestRecord"))),
            List.of(),
            List.of(new CassandraUdtAnnotationProcessor()),
            """
                @ru.tinkoff.kora.database.cassandra.annotation.UDT
                public record TestRecord(String f1, int f2, Nested f3) {
                    @ru.tinkoff.kora.database.cassandra.annotation.UDT
                    public record Nested(String f1){}
                }
                """
        );

        @SuppressWarnings("unchecked")
        var mapper = (CassandraRowColumnMapper<List<Object>>) graph.get(graph.draw().getNodes().get(1));
        var udtValue = randomValue();
        var row = new TestGettableByName(List.of(new TestGettableByName.Value<>(TypeCodecs.listOf(TypeCodecs.udtOf(udtType)), "column", List.of(udtValue))));
        var parsedUdt = mapper.apply(row, 0);

        assertThat(parsedUdt)
            .hasSize(1)
            .element(0)
            .hasFieldOrPropertyWithValue("f1", udtValue.getString(0))
            .hasFieldOrPropertyWithValue("f2", udtValue.getInt(1))
            .extracting("f3")
            .hasFieldOrPropertyWithValue("f1", udtValue.getUdtValue(2).getString(0))
        ;
    }

    @Test
    public void testUdtParameterColumnMapper() {
        var graph = compile(
            ParameterizedTypeName.get(ClassName.get(CassandraParameterColumnMapper.class), ClassName.get(testPackage(), "TestRecord")),
            List.of(),
            List.of(new CassandraUdtAnnotationProcessor()),
            """
                @ru.tinkoff.kora.database.cassandra.annotation.UDT
                public record TestRecord(String f1, int f2, Nested f3) {
                    @ru.tinkoff.kora.database.cassandra.annotation.UDT
                    public record Nested(String f1){}
                }
                """
        );

        @SuppressWarnings("unchecked")
        var mapper = (CassandraParameterColumnMapper<Object>) graph.get(graph.draw().getNodes().get(1));
        var object = newObject(
            "TestRecord",
            UUID.randomUUID().toString(),
            ThreadLocalRandom.current().nextInt(),
            newObject(
                "TestRecord$Nested",
                UUID.randomUUID().toString()
            )
        );
        var statement = new TestSettableByName(List.of(new TestSettableByName.Column("column", udtType)));
        mapper.apply(statement, 0, object);

        var udtValue = TypeCodecs.udtOf(udtType).decode(statement.getData(0), ProtocolVersion.DEFAULT);

        assertThat(object)
            .hasFieldOrPropertyWithValue("f1", udtValue.getString(0))
            .hasFieldOrPropertyWithValue("f2", udtValue.getInt(1))
            .extracting("f3")
            .hasFieldOrPropertyWithValue("f1", udtValue.getUdtValue(2).getString(0))
        ;
    }

    @Test
    public void testUdtListParameterColumnMapper() {
        var graph = compile(
            ParameterizedTypeName.get(ClassName.get(CassandraParameterColumnMapper.class), ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(testPackage(), "TestRecord"))),
            List.of(),
            List.of(new CassandraUdtAnnotationProcessor()),
            """
                @ru.tinkoff.kora.database.cassandra.annotation.UDT
                public record TestRecord(String f1, int f2, Nested f3) {
                    @ru.tinkoff.kora.database.cassandra.annotation.UDT
                    public record Nested(String f1){}
                }
                """
        );

        @SuppressWarnings("unchecked")
        var mapper = (CassandraParameterColumnMapper<List<Object>>) graph.get(graph.draw().getNodes().get(1));
        var object = newObject(
            "TestRecord",
            UUID.randomUUID().toString(),
            ThreadLocalRandom.current().nextInt(),
            newObject(
                "TestRecord$Nested",
                UUID.randomUUID().toString()
            )
        );
        var statement = new TestSettableByName(List.of(new TestSettableByName.Column("column", DataTypes.listOf(udtType))));
        mapper.apply(statement, 0, List.of(object));

        var udtList = TypeCodecs.listOf(TypeCodecs.udtOf(udtType)).decode(statement.getData(0), ProtocolVersion.DEFAULT);
        assertThat(udtList).hasSize(1);
        var udtValue = udtList.get(0);

        assertThat(object)
            .hasFieldOrPropertyWithValue("f1", udtValue.getString(0))
            .hasFieldOrPropertyWithValue("f2", udtValue.getInt(1))
            .extracting("f3")
            .hasFieldOrPropertyWithValue("f1", udtValue.getUdtValue(2).getString(0))
        ;
    }

}
