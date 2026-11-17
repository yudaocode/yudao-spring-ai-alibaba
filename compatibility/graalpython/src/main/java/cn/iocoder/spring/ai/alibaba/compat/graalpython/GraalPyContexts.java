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

package cn.iocoder.spring.ai.alibaba.compat.graalpython;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.graalvm.polyglot.Context;

/**
 * Supplies a portable Python3.8 context with its packaged standard library on Java8.
 * No native binaries, LLVM language provider or global language-home property is added.
 *
 * @author 芋道源码
 */
public final class GraalPyContexts {

    private static Path home;

    private GraalPyContexts() {
    }

    public static Context.Builder newContextBuilder() {
        return newContextBuilder("python");
    }

    public static Context.Builder newContextBuilder(String... permittedLanguages) {
        Path root = languageHome();
        return Context.newBuilder(permittedLanguages)
                .allowExperimentalOptions(true)
                .allowNativeAccess(false)
                .option("python.CoreHome", root.resolve("lib-graalpython").toString())
                .option("python.StdLibHome", root.resolve("lib-python/3").toString())
                .option("python.SysPrefix", root.toString())
                .option("python.PosixModuleBackend", "java")
                .option("python.ForceImportSite", "true")
                .option("engine.WarnInterpreterOnly", "false");
    }

    private static synchronized Path languageHome() {
        if (home != null) return home;
        Path temporary = null;
        try {
            temporary = Files.createTempDirectory("yudao-graalpython22-");
            int files = 0;
            try (InputStream input = GraalPyContexts.class.getResourceAsStream("/META-INF/graalpython/home.zip")) {
                if (input == null) throw new IOException("Packaged Python home is missing");
                try (ZipInputStream zip = new ZipInputStream(input)) {
                    for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                        Path target = temporary.resolve(entry.getName()).normalize();
                        if (!target.startsWith(temporary)) throw new IOException("Invalid Python resource path");
                        if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                        Files.createDirectories(target.getParent());
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                        files++;
                    }
                }
            }
            if (files != 911) throw new IOException("Incomplete Python home: " + files);
            registerCleanup(temporary);
            home = temporary;
            return home;
        }
        catch (IOException failure) {
            if (temporary != null) registerCleanup(temporary);
            throw new IllegalStateException("Unable to prepare the packaged Python standard library", failure);
        }
    }

    private static void registerCleanup(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> all = paths.sorted().collect(Collectors.toList());
            // deleteOnExit deletes in reverse registration order: children before parents.
            for (Path path : all) path.toFile().deleteOnExit();
        }
        catch (IOException failure) {
            directory.toFile().deleteOnExit();
        }
    }

}
