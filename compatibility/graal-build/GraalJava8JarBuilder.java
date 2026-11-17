/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Builds Java8 language jars from pinned upstream inputs without changing class versions.
 * This build tool runs on JDK17; it is not included in runtime classpaths.
 *
 * @author 芋道源码
 */
public final class GraalJava8JarBuilder {

    private final Path module;
    private final Path output;
    private final Path sources;
    private final Map<String, String> relocations = new LinkedHashMap<>();
    private final List<String> generated = new ArrayList<>();
    private boolean adaptBuffers;
    private int bufferCalls;
    private static final Set<String> BUFFERS = new HashSet<>(Arrays.asList("java/nio/ByteBuffer", "java/nio/CharBuffer", "java/nio/DoubleBuffer",
            "java/nio/FloatBuffer", "java/nio/IntBuffer", "java/nio/LongBuffer", "java/nio/ShortBuffer"));
    private static final Set<String> BUFFER_NO_ARGUMENT = new HashSet<>(Arrays.asList("clear", "flip", "mark", "reset", "rewind"));

    private GraalJava8JarBuilder(Path module) {
        this.module = module.toAbsolutePath().normalize();
        this.output = this.module.resolve("target/classes");
        this.sources = this.module.resolve("target/upstream-sources");
    }

    public static void main(String[] args) throws Exception {
        GraalJava8JarBuilder builder = new GraalJava8JarBuilder(Path.of(args[1]));
        Properties input = new Properties();
        try (InputStream stream = Files.newInputStream(builder.module.resolve("upstream.properties"))) {
            input.load(stream);
        }
        if ("js".equals(args[0])) {
            builder.relocations.put("com.ibm.icu", "cn.iocoder.spring.ai.alibaba.compat.graaljs.icu");
            Path binary = builder.module.resolve("target/upstream/js.jar");
            Path source = builder.module.resolve("target/upstream/js-sources.jar");
            verify(binary, input.getProperty("binary.sha256"));
            verify(source, input.getProperty("sources.sha256"));
            builder.removePreviousOutputs();
            builder.copyClasses(binary);
            builder.copySources(source);
        }
        else if ("python".equals(args[0])) {
            builder.python(input, Path.of(args[2]));
        }
        else throw new IllegalArgumentException("Unsupported language " + args[0]);
        Files.write(builder.module.resolve("target/graal-vendor-files.txt"), builder.generated, StandardCharsets.UTF_8);
        System.out.println("Built language=" + args[0] + " classes/resources=" + builder.generated.size()
                + " Java9 Buffer calls adapted=" + builder.bufferCalls);
    }

    private void python(Properties input, Path cache) throws Exception {
        this.relocations.put("com.ibm.icu", "cn.iocoder.spring.ai.alibaba.compat.graalpython.icu");
        this.relocations.put("org.tukaani.xz", "cn.iocoder.spring.ai.alibaba.compat.graalpython.xz");
        this.relocations.put("org.antlr.v4", "cn.iocoder.spring.ai.alibaba.compat.graalpython.antlr");
        this.adaptBuffers = true;
        Path installer = cached(cache, "python-installable.jar", input.getProperty("installer.url"), input.getProperty("installer.sha256"));
        Path archive = cached(cache, "python-source.zip", input.getProperty("source.url"), input.getProperty("source.sha256"));
        Path binary = this.module.resolve("target/upstream/python.jar");
        Files.createDirectories(binary.getParent());
        try (JarFile jar = new JarFile(installer.toFile())) {
            try (InputStream stream = jar.getInputStream(jar.getJarEntry("languages/python/graalpython.jar"))) {
                Files.copy(stream, binary, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        verify(binary, input.getProperty("language.sha256"));
        Path antlr = this.module.resolve("target/upstream/antlr.jar");
        Path antlrSources = this.module.resolve("target/upstream/antlr-sources.jar");
        verify(antlr, input.getProperty("antlr.sha256"));
        verify(antlrSources, input.getProperty("antlr.sources.sha256"));
        removePreviousOutputs();
        copyClasses(binary);
        copyClasses(antlr);
        copySources(antlrSources);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            String marker = "/graalpython/com.oracle.graal.python/src/";
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                int offset = entry.getName().indexOf(marker);
                if (entry.isDirectory() || offset < 0 || !entry.getName().endsWith(".java")) continue;
                String name = entry.getName().substring(offset + marker.length());
                try (InputStream stream = zip.getInputStream(entry)) {
                    Path target = safe(this.sources, rewrite(name));
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, rewrite(new String(stream.readAllBytes(), StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                }
            }
        }
        Path patches = this.module.resolve("src/patches/java");
        Path compiled = this.module.resolve("target/compiled-patches");
        if (Files.exists(compiled)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(compiled)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path);
            }
        }
        Files.createDirectories(compiled);
        List<String> files;
        try (java.util.stream.Stream<Path> paths = Files.walk(patches)) {
            files = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toString).collect(Collectors.toList());
        }
        List<String> options = new ArrayList<>(Arrays.asList("--release", "8", "-proc:none", "-encoding", "UTF-8", "-classpath",
                binary + java.io.File.pathSeparator + antlr + java.io.File.pathSeparator + System.getProperty("java.class.path"),
                "-d", compiled.toString()));
        options.addAll(files);
        if (ToolProvider.getSystemJavaCompiler().run(null, System.out, System.err, options.toArray(new String[0])) != 0) {
            throw new IllegalStateException("Python Java8 source patch compilation failed");
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(compiled)) {
            for (Path file : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String name = compiled.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                byte[] bytes = relocatedClass(Files.readAllBytes(file));
                Path target = safe(this.output, rewrite(name));
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                if (!this.generated.contains(rewrite(name))) this.generated.add(rewrite(name));
            }
        }
        for (String file : files) {
            Path path = Path.of(file);
            Path target = safe(this.sources, rewrite(patches.relativize(path).toString()));
            Files.createDirectories(target.getParent());
            Files.writeString(target, rewrite(Files.readString(path, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        }
        Path homeZip = this.output.resolve("META-INF/graalpython/home.zip");
        Files.createDirectories(homeZip.getParent());
        int homeFiles = 0;
        try (JarFile jar = new JarFile(installer.toFile()); ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(homeZip))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !(name.startsWith("languages/python/lib-python/") || name.startsWith("languages/python/lib-graalpython/"))) continue;
                String relative = name.substring("languages/python/".length());
                ZipEntry target = new ZipEntry(relative);
                target.setTime(0);
                zip.putNextEntry(target);
                try (InputStream stream = jar.getInputStream(entry)) { stream.transferTo(zip); }
                zip.closeEntry();
                homeFiles++;
            }
            for (String license : Arrays.asList("LICENSE_GRAALPYTHON.txt", "THIRD_PARTY_LICENSE_GRAALPYTHON.txt")) {
                String name = "META-INF/licenses/graalpython/" + license;
                Path target = safe(this.output, name);
                Files.createDirectories(target.getParent());
                try (InputStream stream = jar.getInputStream(jar.getJarEntry(license))) { Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING); }
                this.generated.add(name);
                Path sourceTarget = safe(this.sources, name);
                Files.createDirectories(sourceTarget.getParent());
                Files.copy(target, sourceTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        this.generated.add("META-INF/graalpython/home.zip");
        if (homeFiles != 911) throw new IllegalStateException("Unexpected portable Python home file count " + homeFiles);
        System.out.println("Compiled real Python/LLVM API source patches=" + files.size() + "; portable stdlib files=" + homeFiles);
    }

    private static Path cached(Path cache, String name, String url, String sha256) throws Exception {
        Files.createDirectories(cache);
        Path target = cache.resolve(name);
        try (FileChannel channel = FileChannel.open(cache.resolve(name + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            if (Files.exists(target)) { verify(target, sha256); return target; }
            System.out.println("Downloading pinned upstream " + url);
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(120000);
            Path temporary = Files.createTempFile(cache, name + "-", ".part");
            try {
                try (InputStream input = connection.getInputStream()) { Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING); }
                verify(temporary, sha256);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            finally { Files.deleteIfExists(temporary); }
        }
        return target;
    }

    private static void verify(Path path, String expected) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) != -1;) hash.update(buffer, 0, read);
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : hash.digest()) actual.append(String.format("%02x", value & 0xff));
        if (expected == null || !actual.toString().equals(expected)) {
            throw new IllegalStateException("Upstream checksum mismatch for " + path);
        }
    }

    private void removePreviousOutputs() throws Exception {
        Path manifest = this.module.resolve("target/graal-vendor-files.txt");
        if (Files.exists(manifest)) {
            for (String entry : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                Files.deleteIfExists(safe(this.output, entry));
            }
        }
        Files.createDirectories(this.output);
        Files.createDirectories(this.sources);
    }

    private static Path safe(Path root, String entry) {
        Path target = root.resolve(entry).normalize();
        if (!target.startsWith(root) || Path.of(entry).isAbsolute()) {
            throw new IllegalArgumentException("Invalid archive entry " + entry);
        }
        return target;
    }

    private String rewrite(String value) {
        for (Map.Entry<String, String> entry : this.relocations.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue())
                    .replace(entry.getKey().replace('.', '/'), entry.getValue().replace('.', '/'));
        }
        return value;
    }

    private boolean excluded(String name) {
        return name.equals("module-info.class") || name.equals("module-info.java")
                || name.equals("META-INF/MANIFEST.MF") || name.equals("META-INF/INDEX.LIST")
                || name.startsWith("META-INF/versions/") || name.startsWith("META-INF/native-image/")
                || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)");
    }

    private void copyClasses(Path path) throws Exception {
        int classes = 0;
        try (JarFile jar = new JarFile(path.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || excluded(entry.getName())) continue;
                String name = rewrite(entry.getName());
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) { bytes = input.readAllBytes(); }
                if (name.endsWith(".class")) {
                    int version = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
                    if (version > 52) throw new IllegalStateException("Not Java8 bytecode: " + entry.getName());
                    bytes = relocatedClass(bytes);
                    classes++;
                }
                else if (name.startsWith("META-INF/services/") || name.equals("META-INF/truffle/language")) {
                    bytes = rewrite(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                }
                Path target = safe(this.output, name);
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
                this.generated.add(name);
            }
        }
        System.out.println("Verified Java8 base classes=" + classes + "; no class-version lowering");
    }

    private byte[] relocatedClass(byte[] bytes) {
        Remapper mapper = new Remapper() {
            @Override
            public String map(String name) { return rewrite(name); }

            @Override
            public Object mapValue(Object value) {
                return value instanceof String ? rewrite((String) value) : super.mapValue(value);
            }
        };
        if (this.adaptBuffers) {
            ClassWriter adapted = new ClassWriter(0);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, adapted) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            Type result = Type.getReturnType(descriptor);
                            Type[] arguments = Type.getArgumentTypes(descriptor);
                            boolean shape = BUFFER_NO_ARGUMENT.contains(name) && arguments.length == 0
                                    || (name.equals("position") || name.equals("limit")) && arguments.length == 1 && arguments[0].equals(Type.INT_TYPE);
                            if (opcode == Opcodes.INVOKEVIRTUAL && BUFFERS.contains(owner) && result.getSort() == Type.OBJECT
                                    && BUFFERS.contains(result.getInternalName()) && shape) {
                                String base = descriptor.substring(0, descriptor.indexOf(')') + 1) + "Ljava/nio/Buffer;";
                                super.visitMethodInsn(opcode, "java/nio/Buffer", name, base, false);
                                super.visitTypeInsn(Opcodes.CHECKCAST, result.getInternalName());
                                bufferCalls++;
                            }
                            else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }
                    };
                }
            }, 0);
            bytes = adapted.toByteArray();
        }
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassRemapper(writer, mapper), 0);
        return writer.toByteArray();
    }

    private void copySources(Path path) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || excluded(entry.getName())) continue;
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) { bytes = input.readAllBytes(); }
                if (entry.getName().endsWith(".java")) {
                    bytes = rewrite(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                }
                Path target = safe(this.sources, rewrite(entry.getName()));
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            }
        }
    }

}
