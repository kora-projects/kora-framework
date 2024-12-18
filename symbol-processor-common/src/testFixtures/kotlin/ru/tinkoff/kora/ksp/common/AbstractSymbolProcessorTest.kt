package ru.tinkoff.kora.ksp.common

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.classgraph.ClassGraph
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Future
import java.util.stream.Collectors
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
abstract class AbstractSymbolProcessorTest {

    enum class ProcessorOptions(val value: String) {
        SUBMODULE_GENERATION("kora.app.submodule.enabled=true")
    }

    protected lateinit var testInfo: TestInfo
    protected lateinit var compileResult: CompileResult

    @BeforeEach
    fun beforeEach(testInfo: TestInfo) {
        this.testInfo = testInfo
        val testClass: Class<*> = this.testInfo.getTestClass().get()
        val testMethod: Method = this.testInfo.getTestMethod().get()
        val sources = Paths.get(".", "build", "in-test-generated-ksp", "sources")
        val path = sources
            .resolve(testClass.getPackage().name.replace('.', '/'))
            .resolve("packageFor" + testClass.simpleName)
            .resolve(testMethod.name)
        path.toFile().deleteRecursively()
        Files.createDirectories(path)
    }

    @AfterEach
    fun afterEach() {
        if (this::compileResult.isInitialized && this.compileResult.exitCode == ExitCode.OK) {
            compileResult.classLoader.let { if (it is AutoCloseable) it.close() }
        }
    }

    protected fun loadClass(className: String) = this.compileResult.loadClass(className)

    protected fun testPackage(): String {
        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()
        return testClass.packageName + ".packageFor" + testClass.simpleName + "." + testMethod.name
    }

    protected open fun commonImports(): String {
        return """
            import ru.tinkoff.kora.common.annotation.*;
            import ru.tinkoff.kora.common.*;
            import jakarta.annotation.Nullable;
            
            """.trimIndent()
    }

    @Deprecated("k2 will require to pass processors", replaceWith = ReplaceWith("compile0(processors, sources)"))
    protected fun compile0(@Language("kotlin") vararg sources: String): CompileResult {
        return compile0(listOf(), *sources)
    }

    // processors will be used in k2
    protected fun compile0(processors: List<SymbolProcessorProvider>, @Language("kotlin") vararg sources: String): CompileResult {
        val testPackage = testPackage()
        val testClass: Class<*> = testInfo.testClass.get()
        val testMethod: Method = testInfo.testMethod.get()
        val commonImports = commonImports()
        val sourceList: List<String> =
            Arrays.stream(sources).map { s: String -> "package %s;\n%s\n/**\n* @see %s.%s \n*/\n".formatted(testPackage, commonImports, testClass.canonicalName, testMethod.name) + s }
                .map { s ->
                    val firstClass = s.indexOf("class ") to "class ".length
                    val firstInterface = s.indexOf("interface ") to "interface ".length
                    val classNameLocation = sequenceOf(firstClass, firstInterface)
                        .filter { it.first >= 0 }
                        .map { it.first + it.second }
                        .flatMap {
                            sequenceOf(
                                s.indexOf(" ", it + 1),
                                s.indexOf("(", it + 1),
                                s.indexOf("{", it + 1),
                                s.indexOf(":", it + 1),
                            )
                                .map { it1 -> it to it1 }
                        }
                        .filter { it.second >= 0 }
                        .minBy { it.second }
                    val className = s.substring(classNameLocation.first - 1, classNameLocation.second)
                        .trim()
                        .replaceFirst(Regex("<.*>"), "")
                        .trim()
                    val fileName = "build/in-test-generated-ksp/sources/${testPackage.replace('.', '/')}/$className.kt"
                    Files.createDirectories(File(fileName).toPath().parent)
                    Files.deleteIfExists(Paths.get(fileName))
                    Files.writeString(Paths.get(fileName), s, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
                    fileName
                }
                .toList()
        return this.symbolProcessFiles(sourceList)
    }

    class CompilationFailedException(message: String) : RuntimeException(message)

    data class CompileResult(val testPackage: String, val exitCode: ExitCode, val classLoader: ClassLoader, val messages: List<String>) {
        fun loadClass(className: String): Class<*> {
            return classLoader.loadClass("$testPackage.$className")!!
        }

        fun isFailed(): Boolean {
            return exitCode != ExitCode.OK
        }

        fun compilationException(): Throwable {
            val errorMessages = mutableListOf<String>()
            val indexOfFirst = messages.indexOfFirst { it.contains("error: ") || it.contains("exception: ") }
            if (indexOfFirst >= 0) {
                errorMessages.add(messages[indexOfFirst])
                for (i in indexOfFirst + 1 until messages.size) {
                    val message = messages[i]
                    if (message.contains("error: [") || message.contains("warn: [") || message.contains("info: [") || message.contains("logging: [")) {
                        break
                    } else {
                        errorMessages.add(message)
                    }
                }
            }
            throw CompilationFailedException(errorMessages.joinToString("\n"))
        }

        fun assertSuccess() {
            if (isFailed()) {
                throw compilationException()
            }
        }
    }

    protected open fun symbolProcessFiles(srcFiles: List<String>): CompileResult {
        return symbolProcessFiles(srcFiles, listOf())
    }

    @OptIn(ExperimentalPathApi::class)
    protected open fun symbolProcessFiles(srcFiles: List<String>, processorOptions: List<ProcessorOptions>): CompileResult {
        val k2JvmArgs = K2JVMCompilerArguments()
        val kotlinOutPath = Path.of("build/in-test-generated-ksp").toAbsolutePath()
        val inTestGeneratedDestination = kotlinOutPath.resolveSibling("in-test-generated-destination")
        val kotlinOutputDir = kotlinOutPath.resolveSibling("in-test-generated-kotlinOutputDir")
        inTestGeneratedDestination.deleteRecursively()
        kotlinOutputDir.deleteRecursively()
        kotlinOutputDir.createDirectories()

        k2JvmArgs.noReflect = true
        k2JvmArgs.noStdlib = true
        k2JvmArgs.noJdk = false
        k2JvmArgs.includeRuntime = false
        k2JvmArgs.script = false
        k2JvmArgs.disableStandardScript = true
        k2JvmArgs.help = false
        k2JvmArgs.compileJava = true
        k2JvmArgs.allowNoSourceFiles = false
        k2JvmArgs.expression = null
        k2JvmArgs.destination = inTestGeneratedDestination.toString()
        k2JvmArgs.jvmTarget = "17"
        k2JvmArgs.jvmDefault = "all"
        k2JvmArgs.freeArgs = srcFiles
        k2JvmArgs.assertionsMode
        k2JvmArgs.classpath = classpath.joinToString(File.pathSeparator)
        k2JvmArgs.javacArguments = processorOptions.map { o -> o.value }.toTypedArray()

        val pluginClassPath = classpath.asSequence()
            .filter { it.contains("symbol-processing") }
            .toList()
            .toTypedArray()
        val processors = classpath.stream()
            .filter { it.contains("symbol-processor") || it.contains("scheduling-ksp") }
            .collect(Collectors.joining(File.pathSeparator))
        k2JvmArgs.pluginClasspaths = pluginClassPath
        val ksp = "plugin:com.google.devtools.ksp.symbol-processing:"
        k2JvmArgs.pluginOptions = arrayOf(
            ksp + "kotlinOutputDir=" + kotlinOutputDir,
            ksp + "kspOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-kspOutputDir"),
            ksp + "classOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-classOutputDir"),
            ksp + "incremental=false",
            ksp + "withCompilation=true",
            ksp + "javaOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-javaOutputDir"),
            ksp + "projectBaseDir=" + Path.of(".").toAbsolutePath(),
            ksp + "resourceOutputDir=" + kotlinOutPath.resolveSibling("in-test-generated-resourceOutputDir"),
            ksp + "cachesDir=" + kotlinOutPath.resolveSibling("in-test-generated-cachesDir"),
            ksp + "apclasspath=" + processors
        )
        k2JvmArgs.pluginOptions = k2JvmArgs.pluginOptions!! + processorOptions.map { o -> ksp + "apoption=" + o.value }.toTypedArray()

        val sw = ByteArrayOutputStream()
        val collector = PrintingMessageCollector(
            PrintStream(sw, true, StandardCharsets.UTF_8), MessageRenderer.SYSTEM_INDEPENDENT_RELATIVE_PATHS, false
        )
        val co = K2JVMCompiler()
        var code = co.exec(collector, Services.EMPTY, k2JvmArgs)
        kotlinOutPath.resolve("sources").createDirectories()
        kotlinOutputDir.copyToRecursively(
            kotlinOutPath.resolve("sources"),
            followLinks = false,
            overwrite = true
        )
        var cl = Thread.currentThread().contextClassLoader
        if (code != ExitCode.OK) {
            compileResult = CompileResult(testPackage(), code, cl, sw.toString(StandardCharsets.UTF_8).split("\n"))
            return compileResult
        }
        if (collector.hasErrors()) {
            compileResult = CompileResult(testPackage(), code, cl, sw.toString(StandardCharsets.UTF_8).split("\n"))
            return compileResult
        }
        cl = URLClassLoader("test-cl", arrayOf(URL("file://$inTestGeneratedDestination/")), cl)
        println("$code:\n$sw")
        compileResult = CompileResult(testPackage(), code, cl, sw.toString(StandardCharsets.UTF_8).split("\n"))
        return compileResult
    }

    protected fun new(name: String, vararg args: Any?) = compileResult.loadClass(name).constructors[0].newInstance(*args)!!

    interface GeneratedObject<T> : () -> T

    protected fun newGenerated(name: String, vararg args: Any?) = object : GeneratedObject<Any> {
        override fun invoke() = compileResult.loadClass(name).constructors[0].newInstance(*args)!!
    }

    protected fun newObject(name: String, vararg args: Any?): TestObject {
        val loadClass = compileResult.loadClass(name)
        val inst = loadClass.constructors[0].newInstance(*args)!!
        return TestObject(loadClass.kotlin, inst)
    }

    class TestObject(
        val objectClass: KClass<*>,
        val objectInstance: Any
    ) {

        @SuppressWarnings("unchecked")
        fun <T> invoke(method: String, vararg args: Any?): T? {
            for (repositoryClassMethod in objectClass.memberFunctions) {
                if (repositoryClassMethod.name == method && repositoryClassMethod.parameters.size == args.size + 1) {
                    try {
                        val realArgs = Array(args.size + 1) {
                            if (it == 0) {
                                objectInstance
                            } else {
                                args[it - 1]
                            }
                        }

                        val result = if (repositoryClassMethod.isSuspend) {
                            runBlocking(Context.Kotlin.asCoroutineContext(Context.current())) { repositoryClassMethod.callSuspend(*realArgs) }
                        } else {
                            repositoryClassMethod.call(*realArgs)
                        }
                        return when (result) {
                            is Mono<*> -> result.block()
                            is Future<*> -> result.get()
                            else -> result
                        } as T?
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                }
            }
            throw IllegalArgumentException()
        }
    }


    companion object {
        var classpath: MutableList<String>

        init {

            val classGraph = ClassGraph()
                .enableSystemJarsAndModules()
                .removeTemporaryFilesAfterScan()

            val classpaths = classGraph.classpathFiles
            val modules = classGraph.modules
                .asSequence()
                .filterNotNull()
                .map { it.locationFile }

            classpath = (classpaths.asSequence() + modules)
                .filterNotNull()
                .map { it.toString() }
                .distinct()
                .toMutableList()
        }
    }
}
