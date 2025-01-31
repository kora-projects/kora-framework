package ru.tinkoff.kora.database.annotation.processor.cassandra.extension;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.*;
import ru.tinkoff.kora.common.annotation.Generated;
import ru.tinkoff.kora.database.annotation.processor.DbEntityReadHelper;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraNativeTypes;
import ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraTypes;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.ExtensionResult;
import ru.tinkoff.kora.kora.app.annotation.processor.extension.KoraExtension;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static ru.tinkoff.kora.database.annotation.processor.cassandra.CassandraTypes.RESULT_SET;

public class CassandraTypesExtension implements KoraExtension {

    private final Types types;
    private final Elements elements;
    private final Filer filer;
    private final DbEntityReadHelper rowMapperGenerator;

    public CassandraTypesExtension(ProcessingEnvironment env) {
        this.types = env.getTypeUtils();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
        this.rowMapperGenerator = new DbEntityReadHelper(
            CassandraTypes.RESULT_COLUMN_MAPPER,
            this.types,
            fd -> CodeBlock.of("this.$L.apply(_row, _idx_$L)", fd.mapperFieldName(), fd.fieldName()),
            fd -> {
                var nativeType = CassandraNativeTypes.findNativeType(TypeName.get(fd.type()));
                if (nativeType != null) {
                    return nativeType.extract("_row", CodeBlock.of("_idx_$L", fd.fieldName()));
                } else {
                    return null;
                }
            },
            fd -> CodeBlock.builder()
                .beginControlFlow("if (_row.isNull(_idx_$L))", fd.fieldName())
                .add(fd.nullable()
                    ? CodeBlock.of("$N = null;\n", fd.fieldName())
                    : CodeBlock.of("throw new $T($S);\n", NullPointerException.class, "Result field %s is not nullable but row %s has null".formatted(fd.fieldName(), fd.columnName()))
                )
                .endControlFlow()
                .build()
        );
    }

    @Nullable
    @Override
    public KoraExtensionDependencyGenerator getDependencyGenerator(RoundEnvironment roundEnvironment, TypeMirror typeMirror, Set<String> tag) {
        if (!tag.isEmpty()) {
            return null;
        }
        var typeName = TypeName.get(typeMirror).withoutAnnotations();
        if (!(typeName instanceof ParameterizedTypeName ptn) || !(typeMirror instanceof DeclaredType dt)) {
            return null;
        }
        if (ptn.rawType.equals(CassandraTypes.RESULT_SET_MAPPER)) {
            return this.generateResultSetMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.ASYNC_RESULT_SET_MAPPER)) {
            return this.generateAsyncResultSetMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.REACTIVE_RESULT_SET_MAPPER)) {
            return this.generateReactiveResultSetMapper(roundEnvironment, ptn, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.ROW_MAPPER)) {
            return this.generateResultRowMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.PARAMETER_COLUMN_MAPPER)) {
            return this.generateParameterColumnMapper(roundEnvironment, dt);
        }
        if (ptn.rawType.equals(CassandraTypes.RESULT_COLUMN_MAPPER)) {
            return this.generateRowColumnMapper(roundEnvironment, dt);
        }
        return null;
    }

    private KoraExtensionDependencyGenerator generateRowColumnMapper(RoundEnvironment roundEnvironment, DeclaredType dt) {
        var entityType = dt.getTypeArguments().get(0);
        var element = (TypeElement) this.types.asElement(entityType);
        if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
            return KoraExtensionDependencyGenerator.generatedFrom(elements, element, CassandraTypes.RESULT_COLUMN_MAPPER);
        }
        if (element.getQualifiedName().contentEquals("java.util.List")) {
            entityType = ((DeclaredType) entityType).getTypeArguments().get(0);
            element = (TypeElement) this.types.asElement(entityType);
            if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, element, "List_CassandraRowColumnMapper");
            }
        }
        return null;
    }

    private KoraExtensionDependencyGenerator generateParameterColumnMapper(RoundEnvironment roundEnvironment, DeclaredType dt) {
        var entityType = dt.getTypeArguments().get(0);
        var element = (TypeElement) this.types.asElement(entityType);
        if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
            return KoraExtensionDependencyGenerator.generatedFrom(elements, element, CassandraTypes.PARAMETER_COLUMN_MAPPER);
        }
        if (element.getQualifiedName().contentEquals("java.util.List")) {
            entityType = ((DeclaredType) entityType).getTypeArguments().get(0);
            element = (TypeElement) this.types.asElement(entityType);
            if (AnnotationUtils.findAnnotation(element, CassandraTypes.UDT_ANNOTATION) != null) {
                return KoraExtensionDependencyGenerator.generatedFrom(elements, element, "List_CassandraParameterColumnMapper");
            }
        }
        return null;
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateResultRowMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        var rowType = typeMirror.getTypeArguments().get(0);
        var entity = DbEntity.parseEntity(this.types, rowType);
        if (entity == null) {
            return null;
        }
        var mapperName = NameUtils.generatedType(entity.typeElement(), CassandraTypes.ROW_MAPPER);
        var packageElement = this.elements.getPackageOf(entity.typeElement());

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var type = TypeSpec.classBuilder(mapperName)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", CassandraTypesExtension.class.getCanonicalName()).build())
                .addSuperinterface(ParameterizedTypeName.get(
                    CassandraTypes.ROW_MAPPER, TypeName.get(entity.typeMirror())
                ))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
            var constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);

            var apply = MethodSpec.methodBuilder("apply")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(CassandraTypes.ROW, "_row")
                .returns(TypeName.get(entity.typeMirror()));
            var read = this.rowMapperGenerator.readEntity("_result", entity);
            read.enrich(type, constructor);
            for (var field : entity.columns()) {
                apply.addCode("var _idx_$L = _row.firstIndexOf($S);\n", field.variableName(), field.columnName());
            }
            apply.addCode(read.block());
            apply.addCode("return _result;\n");

            type.addMethod(constructor.build());
            type.addMethod(apply.build());
            JavaFile.builder(packageElement.getQualifiedName().toString(), type.build()).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateResultSetMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        //CassandraResultSetMapper<List<T>>
        var listType = typeMirror.getTypeArguments().get(0);
        if (!(listType instanceof DeclaredType dt)) {
            return null;
        }
        if (CommonUtils.isList(listType)) {
            var tn = (ParameterizedTypeName) TypeName.get(listType);
            var rowType = dt.getTypeArguments().get(0);
            return this.listResultSetMapper(typeMirror, tn, (DeclaredType) rowType);
        }
        return () -> {
            var singleResultSetMapper = findStaticMethod(CassandraTypes.RESULT_SET_MAPPER, "singleResultSetMapper");
            var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, listType), singleResultSetMapper.asType());
            return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
        };
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateAsyncResultSetMapper(RoundEnvironment roundEnvironment, DeclaredType typeMirror) {
        //CassandraResultSetMapper<List<T>>
        var listType = typeMirror.getTypeArguments().get(0);
        if (!(listType instanceof DeclaredType dt)) {
            return null;
        }
        if (CommonUtils.isList(listType)) {
            return () -> {
                var tn = (ParameterizedTypeName) TypeName.get(listType);
                var rowType = dt.getTypeArguments().get(0);
                var singleResultSetMapper = findStaticMethod(CassandraTypes.ASYNC_RESULT_SET_MAPPER, "list");
                var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
                var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), singleResultSetMapper.asType());
                return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
            };
        }
        return () -> {
            var singleResultSetMapper = findStaticMethod(CassandraTypes.ASYNC_RESULT_SET_MAPPER, "one");
            var tp = (TypeVariable) singleResultSetMapper.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, listType), singleResultSetMapper.asType());
            return ExtensionResult.fromExecutable(singleResultSetMapper, executableType);
        };
    }

    @Nullable
    private KoraExtensionDependencyGenerator generateReactiveResultSetMapper(RoundEnvironment roundEnvironment, ParameterizedTypeName ptn, DeclaredType typeMirror) {
        if (ptn.typeArguments.size() < 2 || !(ptn.typeArguments.get(1) instanceof ParameterizedTypeName publisherTypeName)) {
            return null;
        }
        if (publisherTypeName.rawType.equals(CommonClassNames.flux)) {
            var fluxMapper = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "flux");
            var rowType = typeMirror.getTypeArguments().get(0);
            var tp = (TypeVariable) fluxMapper.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), fluxMapper.asType());
            return () -> ExtensionResult.fromExecutable(fluxMapper, executableType);
        }
        if (publisherTypeName.rawType.equals(CommonClassNames.mono)) {
            var monoParam = typeMirror.getTypeArguments().get(0);
            var monoParamTypeName = TypeName.get(monoParam);
            if (monoParam instanceof DeclaredType monoDt && monoParamTypeName instanceof ParameterizedTypeName monoPtn && monoPtn.rawType.equals(CommonClassNames.list)) {
                var rowType = monoDt.getTypeArguments().get(0);
                var monoList = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "monoList");
                var tp = (TypeVariable) monoList.getTypeParameters().get(0).asType();
                var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), monoList.asType());
                return () -> ExtensionResult.fromExecutable(monoList, executableType);
            }
            if (monoParamTypeName.equals(TypeName.VOID.box())) {
                var monoVoid = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "monoVoid");
                return () -> ExtensionResult.fromExecutable(monoVoid, (ExecutableType) monoVoid.asType());
            }
            var rowType = monoParam;
            var mono = findStaticMethod(CassandraTypes.REACTIVE_RESULT_SET_MAPPER, "mono");
            var tp = (TypeVariable) mono.getTypeParameters().get(0).asType();
            var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowType), mono.asType());
            return () -> ExtensionResult.fromExecutable(mono, executableType);
        }
        return null;
    }

    private KoraExtensionDependencyGenerator listResultSetMapper(DeclaredType typeMirror, ParameterizedTypeName listType, DeclaredType rowTypeMirror) {
        var packageElement = this.elements.getPackageOf(this.types.asElement(rowTypeMirror));
        var rowType = TypeName.get(rowTypeMirror);
        var entity = DbEntity.parseEntity(this.types, rowTypeMirror);
        if (entity == null) {
            return () -> {
                var listResultSetMapper = findStaticMethod(CassandraTypes.RESULT_SET_MAPPER, "listResultSetMapper");
                var tp = (TypeVariable) listResultSetMapper.getTypeParameters().get(0).asType();
                var executableType = (ExecutableType) GenericTypeResolver.resolve(this.types, Map.of(tp, rowTypeMirror), listResultSetMapper.asType());
                return ExtensionResult.fromExecutable(listResultSetMapper, executableType);
            };
        }
        var rowTypeElement = this.types.asElement(rowTypeMirror);
        var mapperName = NameUtils.generatedType(rowTypeElement, "ListCassandraResultSetMapper");

        return () -> {
            var maybeGenerated = this.elements.getTypeElement(packageElement.getQualifiedName() + "." + mapperName);
            if (maybeGenerated != null) {
                var constructors = CommonUtils.findConstructors(maybeGenerated, m -> m.contains(Modifier.PUBLIC));
                if (constructors.size() != 1) throw new IllegalStateException();
                return ExtensionResult.fromExecutable(constructors.get(0));
            }
            var type = TypeSpec.classBuilder(mapperName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(Generated.class).addMember("value", "$S", CassandraTypesExtension.class.getCanonicalName()).build())
                .addSuperinterface(ParameterizedTypeName.get(
                    CassandraTypes.RESULT_SET_MAPPER, listType
                ));
            var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
            var apply = MethodSpec.methodBuilder("apply")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(listType)
                .addParameter(RESULT_SET, "_rs");
            var read = this.rowMapperGenerator.readEntity("_rowValue", entity);
            read.enrich(type, constructor);
            for (var field : entity.columns()) {
                apply.addCode("var _idx_$L = _rs.getColumnDefinitions().firstIndexOf($S);\n", field.variableName(), field.columnName());
            }
            apply.addCode("var _result = new $T<$T>(_rs.getAvailableWithoutFetching());\n", ArrayList.class, rowTypeMirror);
            apply.beginControlFlow("for (var _row : _rs)");
            apply.addCode(read.block());
            apply.addCode("_result.add(_rowValue);\n");
            apply.endControlFlow();
            apply.addCode("return _result;\n");

            var typeSpec = type.addMethod(apply.build())
                .addMethod(constructor.build())
                .build();
            JavaFile.builder(packageElement.getQualifiedName().toString(), typeSpec).build().writeTo(this.filer);
            return ExtensionResult.nextRound();
        };
    }

    private ExecutableElement findStaticMethod(ClassName className, String methodName) {
        return this.elements.getTypeElement(className.canonicalName()).getEnclosedElements()
            .stream()
            .filter(e -> e.getKind() == ElementKind.METHOD && e.getModifiers().contains(Modifier.STATIC))
            .map(ExecutableElement.class::cast)
            .filter(m -> m.getSimpleName().contentEquals(methodName))
            .findFirst()
            .orElseThrow();
    }
}
