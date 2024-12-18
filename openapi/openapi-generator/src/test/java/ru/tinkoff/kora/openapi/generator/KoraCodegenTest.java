package ru.tinkoff.kora.openapi.generator;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer;
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientAnnotationProcessor;
import ru.tinkoff.kora.http.server.annotation.processor.HttpControllerProcessor;
import ru.tinkoff.kora.json.annotation.processor.JsonAnnotationProcessor;
import ru.tinkoff.kora.validation.annotation.processor.ValidAnnotationProcessor;

import javax.annotation.processing.Processor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.tinkoff.kora.annotation.processor.common.TestUtils.classpath;

class KoraCodegenTest {
    static {
        if (LoggerFactory.getLogger("ROOT") instanceof Logger log) {
            log.setLevel(Level.WARN);
        }
    }

    record SwaggerParams(String mode, String spec, String name, Options options) {

        record Options(boolean authAsArg, boolean jsonNullable) {}
    }

    static SwaggerParams[] generateParams() {
        var result = new ArrayList<SwaggerParams>();
        var modes = new String[]{
//            KoraCodegen.Mode.JAVA_CLIENT.getMode(),
//            KoraCodegen.Mode.JAVA_ASYNC_CLIENT.getMode(),
//            KoraCodegen.Mode.JAVA_REACTIVE_CLIENT.getMode(),
            KoraCodegen.Mode.JAVA_SERVER.getMode(),
//            KoraCodegen.Mode.JAVA_ASYNC_SERVER.getMode(),
//            KoraCodegen.Mode.JAVA_REACTIVE_SERVER.getMode(),
//            KoraCodegen.Mode.KOTLIN_CLIENT.getMode(),
//            KoraCodegen.Mode.KOTLIN_SUSPEND_CLIENT.getMode(),
//            KoraCodegen.Mode.KOTLIN_SERVER.getMode(),
//            KoraCodegen.Mode.KOTLIN_SUSPEND_SERVER.getMode(),
        };

        var files = new String[]{
//            "/example/petstoreV3_enum.yaml",
//            "/example/petstoreV3_form.yaml",
//            "/example/petstoreV3_request_parameters.yaml",
//            "/example/petstoreV3_types.yaml",
//            "/example/petstoreV3_validation.yaml",
//            "/example/petstoreV3_single_response.yaml",
//            "/example/petstoreV3_security_all.yaml",
//            "/example/petstoreV3_security_api_key.yaml",
//            "/example/petstoreV3_security_basic.yaml",
//            "/example/petstoreV3_security_bearer.yaml",
//            "/example/petstoreV3_security_oauth.yaml",
            "/example/petstoreV3_discriminator.yaml",
//            "/example/petstoreV3_nullable.yaml",
//            "/example/petstoreV3.yaml",
//            "/example/petstoreV2.yaml",
        };

        for (var fileName : files) {
            for (var mode : modes) {
                var name = fileName.substring(fileName.lastIndexOf('/') + 1)
                    .replace(".yaml", "")
                    .replace(".json", "");

                result.add(new SwaggerParams(mode, fileName, name, new SwaggerParams.Options(false, false)));
                if (fileName.contains("security")) {
                    result.add(new SwaggerParams(mode, fileName, name + "_auth_arg", new SwaggerParams.Options(true, false)));
                }

                if (fileName.contains("discriminator") || fileName.contains("validation") || fileName.contains("nullable")) {
                    result.add(new SwaggerParams(mode, fileName, name + "_enable_json_nullable", new SwaggerParams.Options(false, true)));
                }
            }
        }

        return result.toArray(SwaggerParams[]::new);
    }

    @ParameterizedTest
    @MethodSource("generateParams")
    void generateTest(SwaggerParams params) throws Exception {
        generate(
            params.name(),
            params.mode(),
            params.spec(),
            "build/out/%s/%s".formatted(params.name(), params.mode().replace('_', '/')),
            params.options()
        );
    }

    private void generate(String name, String mode, String spec, String dir, SwaggerParams.Options options) throws Exception {
        var configurator = new CodegenConfigurator()
            .setGeneratorName("kora")
            .setInputSpec(spec) // or from the server
            .setOutputDir(dir)
            .setApiPackage(dir.replace('/', '.') + ".api")
            .setModelPackage(dir.replace('/', '.') + ".model")
            .setGlobalProperties(Map.of(
                "skipFormModel", "false"
            ))
            .addAdditionalProperty("mode", mode)
            .addAdditionalProperty("additionalModelTypeAnnotations", "@ru.tinkoff.kora.json.common.annotation.JsonInclude(ru.tinkoff.kora.json.common.annotation.JsonInclude.IncludeType.ALWAYS)")
            .addAdditionalProperty("interceptors", """
                {
                  "*": [
                    {
                      "tag": "java.lang.String"
                    }
                  ]
                }
                """)
            .addAdditionalProperty("tags", """
                {
                    "*": {
                      "httpClientTag": "java.lang.String",
                      "telemetryTag": "java.lang.String"
                    }
                  }
                """)
            .addAdditionalProperty("enableServerValidation", name.contains("validation"))
            .addAdditionalProperty("authAsMethodArgument", options.authAsArg())
            .addAdditionalProperty("enableJsonNullable", options.jsonNullable())
            .addAdditionalProperty("clientConfigPrefix", "test");

        var processors = new Processor[]{new JsonAnnotationProcessor(), new HttpClientAnnotationProcessor(), new HttpControllerProcessor(), new ValidAnnotationProcessor(), new AopAnnotationProcessor()};
        var clientOptInput = configurator.toClientOptInput();
        var generator = new DefaultGenerator();
        var files = generator.opts(clientOptInput).generate()
            .stream()
            .map(File::getAbsolutePath)
            .map(String::toString)
            .toList();

        if (mode.contains("kotlin")) {
            compileKotlin(files.stream().filter(f -> f.endsWith(".kt")).toList());
        } else {
            TestUtils.annotationProcessFiles(files.stream().filter(f -> f.endsWith(".java")).toList(), processors);
        }
    }


    public static void compileKotlin(List<String> targetFiles) throws Exception {
        var k2JvmArgs = new K2JVMCompilerArguments();
        var kotlinOutPath = Path.of("build/in-test-generated-ksp").toAbsolutePath();
        var inTestGeneratedDestination = kotlinOutPath.resolveSibling("in-test-generated-destination");
        var kotlinOutputDir = kotlinOutPath.resolveSibling("in-test-generated-kotlinOutputDir");
        if (Files.exists(inTestGeneratedDestination)) for (var file : Files.walk(inTestGeneratedDestination).filter(Files::isRegularFile).toList()) {
            Files.deleteIfExists(file);
        }
        if (Files.exists(kotlinOutputDir)) for (var file : Files.walk(kotlinOutputDir).filter(Files::isRegularFile).toList()) {
            Files.deleteIfExists(file);
        }
        Files.createDirectories(kotlinOutputDir);


        k2JvmArgs.setNoReflect(true);
        k2JvmArgs.setNoStdlib(true);
        k2JvmArgs.setNoJdk(false);
        k2JvmArgs.setIncludeRuntime(false);
        k2JvmArgs.setScript(false);
        k2JvmArgs.setDisableStandardScript(true);
        k2JvmArgs.setHelp(false);
        k2JvmArgs.setCompileJava(false);
        k2JvmArgs.setAllowNoSourceFiles(true);
        k2JvmArgs.setExpression(null);
        k2JvmArgs.setDestination(inTestGeneratedDestination.toString());
        k2JvmArgs.setJvmTarget("17");
        k2JvmArgs.setJvmDefault("all");
        k2JvmArgs.setFreeArgs(targetFiles);
        k2JvmArgs.setClasspath(classpath.stream().map(String::toString).collect(Collectors.joining(File.pathSeparator)));

        var pluginClassPath = classpath.stream()
            .filter(it -> it.contains("symbol-processing"))
            .toList()
            .toArray(new String[0]);
        var processors = classpath.stream()
            .filter(it -> it.contains("symbol-processor") || it.contains("scheduling-ksp"))
            .collect(Collectors.joining(File.pathSeparator));
        k2JvmArgs.setPluginClasspaths(pluginClassPath);
        var ksp = "plugin:com.google.devtools.ksp.symbol-processing:";
        k2JvmArgs.setPluginOptions(new String[]{
            ksp + "kotlinOutputDir=" + kotlinOutputDir,
            ksp + "kspOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-kspOutputDir"),
            ksp + "classOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-classOutputDir"),
            ksp + "incremental=false",
            ksp + "withCompilation=true",
            ksp + "javaOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-javaOutputDir"),
            ksp + "projectBaseDir=" + Path.of(".").toAbsolutePath(),
            ksp + "resourceOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-resourceOutputDir"),
            ksp + "cachesDir=" + kotlinOutPath.resolveSibling("in-test-generated-cachesDir"),
            ksp + "apclasspath=" + processors,
        });

        var sw = new ByteArrayOutputStream();
        var collector = new PrintingMessageCollector(
            new PrintStream(sw, true, StandardCharsets.UTF_8), MessageRenderer.SYSTEM_INDEPENDENT_RELATIVE_PATHS, false
        );
        var co = new K2JVMCompiler();
        var code = co.exec(collector, Services.EMPTY, k2JvmArgs);
        Files.createDirectories(kotlinOutPath.resolve("sources"));
        var iter = Files.walk(kotlinOutputDir).filter(Files::isRegularFile).iterator();
        while (iter.hasNext()) {
            var it = iter.next();
            var to = kotlinOutPath.resolve("sources").resolve(kotlinOutputDir.relativize(it));
            Files.createDirectories(to.getParent());
            Files.copy(it, to, StandardCopyOption.REPLACE_EXISTING);
        }

        if (code != ExitCode.OK) {
            throw new RuntimeException(sw.toString());
        }
        if (collector.hasErrors()) {
            throw new RuntimeException(sw.toString());
        }
    }
}
