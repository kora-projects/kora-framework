package ru.tinkoff.kora.annotation.processor.common;

import com.squareup.javapoet.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.common.NamingStrategy;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.naming.NameConverter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Types;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommonUtils {
    public static String decapitalize(String s) {
        var firstChar = s.charAt(0);
        if (Character.isLowerCase(firstChar)) {
            return s;
        }
        return Character.toLowerCase(firstChar) + s.substring(1);
    }

    public static String capitalize(String s) {
        var firstChar = s.charAt(0);
        if (Character.isUpperCase(firstChar)) {
            return s;
        }
        return Character.toUpperCase(firstChar) + s.substring(1);
    }

    public static boolean isNullable(AnnotatedConstruct element) {
        var isNullable = element.getAnnotationMirrors()
            .stream()
            .anyMatch(a -> a.getAnnotationType().toString().endsWith(".Nullable"));
        if (isNullable) {
            return true;
        }
        if (element instanceof ExecutableElement method) {
            if (method.getReturnType().getKind().isPrimitive()) {
                return false;
            }
            return isNullable(method.getReturnType());
        }
        if (element instanceof VariableElement ve) {
            var type = ve.asType();
            if (type.getKind().isPrimitive()) {
                return false;
            }
            return isNullable(type);
        }
        if (element instanceof RecordComponentElement rce) {
            return rce.getEnclosingElement().getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .filter(e -> e.getSimpleName().contentEquals(rce.getSimpleName()))
                .anyMatch(CommonUtils::isNullable);
        }
        return false;
    }

    public static void safeWriteTo(ProcessingEnvironment processingEnv, JavaFile file) {
        try {
            file.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<ExecutableElement> findConstructors(TypeElement typeElement, Predicate<Set<Modifier>> modifiersFilter) {
        var result = new ArrayList<ExecutableElement>();
        for (var element : typeElement.getEnclosedElements()) {
            if (element.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            if (!modifiersFilter.test(element.getModifiers())) {
                continue;
            }
            result.add((ExecutableElement) element);
        }
        return result;
    }

    public static boolean hasDefaultConstructorAndFinal(Types types, TypeMirror typeMirror) {
        var typeElement = types.asElement(typeMirror);
        if (typeElement instanceof TypeElement te) {
            return hasDefaultConstructorAndFinal(te);
        } else {
            return false;
        }
    }

    public static boolean hasDefaultConstructorAndFinal(TypeElement typeElement) {
        if (!typeElement.getModifiers().contains(Modifier.FINAL)) {
            return false;
        }
        for (var enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            var constructor = (ExecutableElement) enclosedElement;
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (constructor.getParameters().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static List<ExecutableElement> findMethods(TypeElement typeElement, Predicate<Set<Modifier>> modifiersFilter) {
        var result = new ArrayList<ExecutableElement>();
        for (var element : typeElement.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (!modifiersFilter.test(element.getModifiers())) {
                continue;
            }
            result.add((ExecutableElement) element);
        }
        return result;
    }

    public record MappersData(@Nullable List<TypeMirror> mapperClasses, Set<String> mapperTags) {
        @Nullable
        public MappingData getMapping(Types types, TypeMirror type) {
            if (this.mapperClasses == null && this.mapperTags.isEmpty()) {
                return null;
            }
            for (var mapperClass : Objects.requireNonNullElse(mapperClasses, List.<TypeMirror>of())) {
                if (types.isAssignable(mapperClass, type)) {
                    return new MappingData(mapperClass, this.mapperTags);
                }
            }
            if (this.mapperTags.isEmpty()) {
                return null;
            }
            return new MappingData(null, this.mapperTags);
        }

        @Nullable
        public MappingData getMapping(ClassName type) {
            if (this.mapperClasses == null) {
                return null;
            }
            for (var mapperClass : mapperClasses) {
                if (doesImplement(mapperClass, type)) {
                    return new MappingData(mapperClass, this.mapperTags);
                }
            }
            if (this.mapperTags.isEmpty()) {
                return null;
            }
            return new MappingData(null, this.mapperTags);
        }

        public boolean isEmpty() {
            return this.mapperTags == null && (this.mapperClasses == null || this.mapperClasses.isEmpty());
        }

        @Nullable
        public MappingData first() {
            if (isEmpty()) {
                return null;
            }
            if (this.mapperClasses == null || this.mapperClasses.isEmpty()) {
                return new MappingData(null, this.mapperTags);
            }
            return new MappingData(this.mapperClasses.get(0), this.mapperTags);
        }
    }

    public static boolean doesImplement(TypeMirror type, ClassName i) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        var declaredType = (DeclaredType) type;
        var typeElement = (TypeElement) declaredType.asElement();
        for (var anInterface : typeElement.getInterfaces()) {
            var interfaceType = TypeName.get(anInterface);
            if (interfaceType instanceof ParameterizedTypeName ptn) {
                if (ptn.rawType.equals(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    public record MappingData(@Nullable TypeMirror mapperClass, Set<String> mapperTags) {
        @Nullable
        public AnnotationSpec toTagAnnotation() {
            return CommonUtils.toTagAnnotation(mapperTags);
        }

        public boolean isGeneric() {
            return mapperClass instanceof DeclaredType dt
                && dt.asElement() instanceof TypeElement te
                && !te.getTypeParameters().isEmpty();
        }

        public ParameterizedTypeName parameterized(TypeName tn) {
            assert isGeneric();
            if (mapperClass instanceof DeclaredType dt && dt.asElement() instanceof TypeElement te) {
                return ParameterizedTypeName.get(ClassName.get(te), tn);
            }
            throw new IllegalStateException();
        }
    }

    @Nullable
    public static AnnotationSpec toTagAnnotation(Set<String> t) {
        if (t == null || t.isEmpty()) {
            return null;
        }

        var tags = CodeBlock.builder().add("{");
        for (var i = t.iterator(); i.hasNext(); ) {
            var tag = i.next();
            tags.add("$L.class", tag);
            if (i.hasNext()) {
                tags.add(", ");
            }
        }
        tags.add("}");
        return AnnotationSpec.builder(Tag.class).addMember("value", tags.build()).build();
    }

    public static MappersData parseMapping(Element element) {
        var tag = TagUtils.parseTagValue(element);
        if (element.getAnnotationsByType(Mapping.class).length == 0 && tag.isEmpty()) {
            return new MappersData(null, tag);
        }
        var mapping = Stream.of(element.getAnnotationsByType(Mapping.class))
            .map(m -> {
                try {
                    m.value();
                    throw new IllegalStateException();
                } catch (MirroredTypeException e) {
                    return e.getTypeMirror();
                }
            })
            .collect(Collectors.toList());

        return new MappersData(mapping, tag);
    }

    @Nullable
    public static Class<?> getNamingStrategyConverterClass(Element element) {
        var annotation = AnnotationUtils.findAnnotation(element, CommonClassNames.namingStrategy);
        if (annotation == null) {
            return null;
        }
        var typeMirrors = AnnotationUtils.<List<TypeMirror>>parseAnnotationValueWithoutDefault(annotation, "value");
        if (typeMirrors == null || typeMirrors.isEmpty()) return null;
        var mirror = typeMirrors.get(0);
        if (mirror instanceof DeclaredType dt) {
            if (dt.asElement() instanceof TypeElement te) {
                var className = te.getQualifiedName().toString();
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new ProcessingErrorException("Class " + className + " not found in classpath", element);
                }
            }
        }
        return null;
    }

    public static NameConverter getNameConverter(NameConverter defaultValue, TypeElement typeElement) {
        var converter = getNameConverter(typeElement);
        if (converter != null) {
            return converter;
        } else {
            return defaultValue;
        }
    }

    @Nullable
    public static NameConverter getNameConverter(TypeElement typeElement) {
        var namingStrategy = typeElement.getAnnotation(NamingStrategy.class);
        NameConverter nameConverter = null;
        if (namingStrategy != null) {
            var namingStrategyClass = getNamingStrategyConverterClass(typeElement);
            if (namingStrategyClass != null) {
                try {
                    nameConverter = (NameConverter) namingStrategyClass.getConstructor().newInstance();
                } catch (Exception e) {
                    throw new ProcessingErrorException("Error on calling name converter constructor " + typeElement, typeElement);
                }
            }
        }
        return nameConverter;
    }

    public static TypeSpec.Builder extendsKeepAop(TypeElement type, String newName) {
        var b = TypeSpec.classBuilder(newName)
            .addModifiers(Modifier.PUBLIC)
            .addOriginatingElement(type);
        if (type.getKind() == ElementKind.INTERFACE) {
            b.addSuperinterface(type.asType());
        } else {
            b.superclass(type.asType());
        }

        var hasAop = false;
        for (var annotationMirror : type.getAnnotationMirrors()) {
            if (CommonUtils.isAopAnnotation(annotationMirror)) {
                b.addAnnotation(AnnotationSpec.get(annotationMirror));
                hasAop = true;
            }
        }

        if (!hasAop && !hasAopAnnotations(type)) {
            b.addModifiers(Modifier.FINAL);
        }

        return b;
    }

    public static MethodSpec.Builder overridingKeepAop(ExecutableElement method) {
        var type = (ExecutableType) method.asType();
        return overridingKeepAop(method, type);
    }

    public static MethodSpec.Builder overridingKeepAop(ExecutableElement method, ExecutableType methodType) {
        var methodBuilder = MethodSpec.methodBuilder(method.getSimpleName().toString());
        if (method.getModifiers().contains(Modifier.PUBLIC)) {
            methodBuilder.addModifiers(Modifier.PUBLIC);
        }
        if (method.getModifiers().contains(Modifier.PROTECTED)) {
            methodBuilder.addModifiers(Modifier.PROTECTED);
        }
        for (var typeParameterElement : method.getTypeParameters()) {
            var var = (TypeVariable) typeParameterElement.asType();
            methodBuilder.addTypeVariable(TypeVariableName.get(var));
        }
        methodBuilder.addAnnotation(Override.class);
        for (var annotationMirror : method.getAnnotationMirrors()) {
            if (CommonUtils.isAopAnnotation(annotationMirror) || annotationMirror.getAnnotationType().toString().endsWith(".Nullable")) {
                methodBuilder.addAnnotation(AnnotationSpec.get(annotationMirror));
            }
        }


        methodBuilder.returns(TypeName.get(methodType.getReturnType()));
        for (int i = 0; i < method.getParameters().size(); i++) {
            var parameter = method.getParameters().get(i);
            var parameterType = methodType.getParameterTypes().get(i);
            var name = parameterType.toString().startsWith("kotlin.coroutines.Continuation")
                ? "_continuation"
                : parameter.getSimpleName().toString();
            var pb = ParameterSpec.builder(TypeName.get(parameterType), name);
            for (var annotationMirror : parameter.getAnnotationMirrors()) {
                if (CommonUtils.isAopAnnotation(annotationMirror) || annotationMirror.getAnnotationType().toString().endsWith(".Nullable")) {
                    pb.addAnnotation(AnnotationSpec.get(annotationMirror));
                }
            }
            methodBuilder.addParameter(pb.build());
        }
        methodBuilder.varargs(method.isVarArgs());
        for (var thrownType : methodType.getThrownTypes()) {
            methodBuilder.addException(TypeName.get(thrownType));
        }

        return methodBuilder;
    }


    public static boolean hasAopAnnotations(TypeElement typeElement) {
        if (CommonUtils.hasAopAnnotation(typeElement)) {
            return true;
        }
        var methods = CommonUtils.findMethods(typeElement, m -> m.contains(Modifier.PUBLIC) || m.contains(Modifier.PROTECTED));
        for (var method : methods) {
            if (hasAopAnnotation(method)) {
                return true;
            }
            for (var parameter : method.getParameters()) {
                if (hasAopAnnotation(parameter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAopAnnotation(Element e) {
        return e.getAnnotationMirrors().stream().anyMatch(CommonUtils::isAopAnnotation);
    }

    private static boolean isAopAnnotation(AnnotationMirror am) {
        return am.getAnnotationType().asElement().getAnnotation(AopAnnotation.class) != null;
    }

    public static boolean isVoid(TypeMirror returnType) {
        if (returnType.getKind() == TypeKind.NONE || returnType.getKind() == TypeKind.VOID) {
            return true;
        }

        final String typeAsStr = returnType.toString();
        return Void.class.getCanonicalName().equals(typeAsStr) || "void".equals(typeAsStr);
    }

    public static boolean isList(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        if (!(type instanceof DeclaredType dt)) {
            return false;
        }
        var name = dt.asElement().toString();
        return name.equals(List.class.getCanonicalName())
            || name.equals(ArrayList.class.getCanonicalName())
            || name.equals(LinkedList.class.getCanonicalName());
    }

    public static boolean isSet(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        if (!(type instanceof DeclaredType dt)) {
            return false;
        }
        var name = dt.asElement().toString();
        return name.equals(Set.class.getCanonicalName())
            || name.equals(HashSet.class.getCanonicalName())
            || name.equals(TreeSet.class.getCanonicalName())
            || name.equals(SortedSet.class.getCanonicalName())
            || name.equals(LinkedHashSet.class.getCanonicalName())
            || name.equals(CopyOnWriteArraySet.class.getCanonicalName())
            || name.equals(ConcurrentSkipListSet.class.getCanonicalName());
    }

    public static boolean isQueue(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
            && type instanceof DeclaredType dt
            && (dt.asElement().toString().equals(Queue.class.getCanonicalName())
            || dt.asElement().toString().equals(Deque.class.getCanonicalName()));
    }

    public static boolean isCollection(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
            && type instanceof DeclaredType dt
            && (dt.asElement().toString().equals(Collection.class.getCanonicalName())
            || isList(type)
            || isSet(type)
            || isQueue(type));
    }

    public static boolean isMap(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        if (!(type instanceof DeclaredType dt)) {
            return false;
        }
        var name = dt.asElement().toString();

        return name.equals(Map.class.getCanonicalName())
            || name.equals(HashMap.class.getCanonicalName())
            || name.equals(TreeMap.class.getCanonicalName())
            || name.equals(LinkedHashMap.class.getCanonicalName())
            || name.equals(ConcurrentMap.class.getCanonicalName())
            || name.equals(ConcurrentHashMap.class.getCanonicalName())
            || name.equals(SortedMap.class.getCanonicalName())
            || name.equals(NavigableMap.class.getCanonicalName())
            || name.equals(ConcurrentSkipListMap.class.getCanonicalName())
            || name.equals(IdentityHashMap.class.getCanonicalName())
            || name.equals(WeakHashMap.class.getCanonicalName())
            || name.equals(EnumMap.class.getCanonicalName());
    }

    public static boolean isOptional(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
            && type instanceof DeclaredType dt
            && dt.asElement().toString().equals(Optional.class.getCanonicalName());
    }

    public static boolean isMono(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
            && type instanceof DeclaredType dt
            && dt.asElement().toString().equals(CommonClassNames.mono.canonicalName());
    }

    public static boolean isFlux(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
            && type instanceof DeclaredType dt
            && dt.asElement().toString().equals(CommonClassNames.flux.canonicalName());
    }

    public static boolean isPublisher(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED
            && type instanceof DeclaredType dt
            && dt.asElement().toString().equals(CommonClassNames.publisher.canonicalName());
    }

    public static boolean isFuture(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }

        if (!(type instanceof DeclaredType dt)) {
            return false;
        }

        final String name = dt.asElement().toString();
        return name.equals(CompletableFuture.class.getCanonicalName())
            || name.equals(CompletionStage.class.getCanonicalName());
    }
}
