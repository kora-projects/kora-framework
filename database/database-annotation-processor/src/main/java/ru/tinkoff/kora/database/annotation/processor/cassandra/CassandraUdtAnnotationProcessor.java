package ru.tinkoff.kora.database.annotation.processor.cassandra;

import ru.tinkoff.kora.annotation.processor.common.AbstractKoraProcessor;
import ru.tinkoff.kora.database.annotation.processor.entity.DbEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

public class CassandraUdtAnnotationProcessor extends AbstractKoraProcessor {
    private UserDefinedTypeResultExtractorGenerator resultExtractorGenerator;
    private UserDefinedTypeStatementSetterGenerator statementSetterGenerator;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(CassandraTypes.UDT_ANNOTATION.canonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.resultExtractorGenerator = new UserDefinedTypeResultExtractorGenerator(processingEnv);
        this.statementSetterGenerator = new UserDefinedTypeStatementSetterGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.RECORD && element.getKind() != ElementKind.CLASS) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "Only classes and records can be annotated with @UDT", element);
                    continue;
                }
                var type = element.asType();
                var entity = DbEntity.parseEntity(types, type);
                if (entity == null) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "Invalid type for @UDT", element);
                    continue;
                }
                this.statementSetterGenerator.generate(type);
                this.resultExtractorGenerator.generate(type);
            }
        }
        return false;
    }
}
