package com.graphorm.generator.compiler;

import com.graphorm.generator.engine.GeneratedSourceFile;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;

/**
 * In-memory Java compiler test harness and utility leveraging JSR-199 ({@link JavaCompiler}).
 * <p>
 * Compiles generated Java source files entirely in-memory without disk I/O, captures all diagnostics,
 * enforces zero warnings/errors, and loads compiled classes into an isolated {@link ClassLoader}.
 */
public final class InMemoryCompiler {

    private InMemoryCompiler() {}

    /**
     * Compiles a collection of {@link GeneratedSourceFile}s in-memory.
     *
     * @param sourceFiles source files to compile
     * @return the compilation result containing loaded classes and diagnostics
     */
    public static CompilationResult compile(Collection<GeneratedSourceFile> sourceFiles) {
        Objects.requireNonNull(sourceFiles, "Source files must not be null");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("System JavaCompiler not available. Ensure execution runs under a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);

        Map<String, ByteArrayOutputStream> bytecodeMap = new HashMap<>();

        ForwardingJavaFileManager<StandardJavaFileManager> inMemoryFileManager =
                new ForwardingJavaFileManager<>(standardFileManager) {
                    @Override
                    public JavaFileObject getJavaFileForOutput(
                            Location location,
                            String className,
                            JavaFileObject.Kind kind,
                            FileObject sibling
                    ) {
                        return new SimpleJavaFileObject(
                                URI.create("mem:///" + className.replace('.', '/') + kind.extension),
                                kind
                        ) {
                            @Override
                            public OutputStream openOutputStream() {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                bytecodeMap.put(className, baos);
                                return baos;
                            }
                        };
                    }
                };

        List<JavaFileObject> compilationUnits = new ArrayList<>();
        for (GeneratedSourceFile src : sourceFiles) {
            URI uri = URI.create("string:///" + src.relativePath().toString());
            compilationUnits.add(new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return src.content();
                }
            });
        }

        List<String> options = new ArrayList<>();
        options.add("-Xlint:all");
        options.add("-parameters");

        String classPath = System.getProperty("java.class.path");
        if (classPath != null && !classPath.isBlank()) {
            options.add("-classpath");
            options.add(classPath);
        }

        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                inMemoryFileManager,
                diagnostics,
                options,
                null,
                compilationUnits
        );

        boolean success = Boolean.TRUE.equals(task.call());

        ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
        if (parentClassLoader == null) {
            parentClassLoader = InMemoryCompiler.class.getClassLoader();
        }

        ClassLoader inMemoryClassLoader = new ClassLoader(parentClassLoader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                ByteArrayOutputStream baos = bytecodeMap.get(name);
                if (baos != null) {
                    byte[] bytes = baos.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            }
        };

        return new CompilationResult(success, diagnostics.getDiagnostics(), inMemoryClassLoader, bytecodeMap);
    }

    /**
     * Represents the outcome of an in-memory compilation run.
     */
    public record CompilationResult(
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            ClassLoader classLoader,
            Map<String, ByteArrayOutputStream> bytecodeMap
    ) {

        /**
         * Asserts that compilation succeeded with 0 errors and 0 warnings.
         *
         * @throws AssertionError if there are any errors or warnings
         */
        public void assertCleanCompilation() {
            List<String> errorMessages = new ArrayList<>();
            List<String> warningMessages = new ArrayList<>();

            for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
                String msg = String.format("[%s] Line %d, col %d in %s: %s",
                        d.getKind(),
                        d.getLineNumber(),
                        d.getColumnNumber(),
                        d.getSource() != null ? d.getSource().getName() : "unknown",
                        d.getMessage(Locale.ENGLISH));

                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    errorMessages.add(msg);
                } else if (d.getKind() == Diagnostic.Kind.WARNING || d.getKind() == Diagnostic.Kind.MANDATORY_WARNING) {
                    warningMessages.add(msg);
                }
            }

            if (!success || !errorMessages.isEmpty()) {
                throw new AssertionError("Compilation failed with " + errorMessages.size() + " errors:\n" +
                        String.join("\n", errorMessages));
            }

            if (!warningMessages.isEmpty()) {
                throw new AssertionError("Compilation produced " + warningMessages.size() + " warnings (expected 0 warnings):\n" +
                        String.join("\n", warningMessages));
            }
        }

        /**
         * Loads a compiled class by fully-qualified name.
         */
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return classLoader.loadClass(name);
        }
    }
}
