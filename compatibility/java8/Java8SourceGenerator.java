/*
 * Copyright 2023-present the original author or authors.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.PrimitiveType;

/**
 * Generates Java 8-compatible record implementations before javac/Jabel compilation.
 * Source records remain readable and mergeable with upstream; generated output is never
 * used as input. Jabel handles syntax only, while --release 8 checks platform APIs.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class Java8SourceGenerator {

    private static CompilationUnit activeUnit;
    private static Path activeInput;
    private static final java.util.Map<String, java.util.Set<ElementType>> annotationTargets = new java.util.HashMap<>();

    public static void main(String[] args) throws Exception {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_17);
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        activeInput = input;
        java.util.Set<Path> generated = new java.util.HashSet<>();
        if (!Files.isDirectory(input)) {
            removeStale(output, generated);
            return;
        }
        var types = new com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver(
            new com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver(input),
            new com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver(Java8SourceGenerator.class.getClassLoader()));
        StaticJavaParser.getParserConfiguration().setSymbolResolver(new com.github.javaparser.symbolsolver.JavaSymbolSolver(types));
        try (var paths = Files.walk(input)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                if (input.toString().endsWith("src/test/java") && path.getFileName().toString().equals("PythonTool.java")) {
                    // This optional GraalPy test fixture requires a Java 17 runtime.
                    continue;
                }
                if (input.relativize(path).toString().replace('\\', '/').contains("/aot/")
                    || path.getFileName().toString().endsWith("AotProcessor.java")
                    || path.getFileName().toString().endsWith("AotProcessorTests.java")
                    || path.getFileName().toString().endsWith("RuntimeHintsTests.java")) {
                    continue; // Spring 6 native-image infrastructure has no Boot 2 counterpart.
                }
                CompilationUnit unit = StaticJavaParser.parse(path);
                activeUnit = unit;
                unit.getImports().forEach(i -> i.setName(java8Namespace(i.getNameAsString())));
                unit.getImports().forEach(i -> {
                    if (i.getNameAsString().equals("org.springframework.core.task.support.ContextPropagatingTaskDecorator"))
                        i.setName("org.springframework.ai.util.ContextPropagatingTaskDecorator");
                    if (i.getNameAsString().equals("org.springframework.boot.autoconfigure.service.connection.ConnectionDetails"))
                        i.setName("org.springframework.ai.util.ConnectionDetails");
                });
                unit.findAll(AnnotationExpr.class).forEach(a -> a.setName(java8Namespace(a.getNameAsString())));
                unit.findAll(StringLiteralExpr.class).forEach(value -> {
                    String translated = java8Namespace(value.asString());
                    if (!translated.equals(value.asString())) value.setString(translated);
                });
                unit.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class).stream()
                    .filter(type -> type.getScope().isPresent() && !java8Namespace(type.toString()).equals(type.toString()))
                    .forEach(type -> type.replace(StaticJavaParser.parseClassOrInterfaceType(java8Namespace(type.toString()))));
                unit.getImports().stream()
                    .filter(i -> i.getNameAsString().equals("org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration"))
                    .forEach(i -> i.setName("org.springframework.ai.compat.autoconfigure.RestClientAutoConfiguration"));
                if (path.getFileName().toString().equals("AgentSkillsTest.java")) {
                    unit.getImports().removeIf(i -> i.getNameAsString().equals("com.alibaba.cloud.ai.graph.agent.tools.PythonTool"));
                    unit.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).stream()
                        .filter(method -> java.util.Set.of("testReactAgentSkillsIntegration", "testClasspathSkillRegistryIntegration").contains(method.getNameAsString()))
                        .forEach(com.github.javaparser.ast.Node::remove);
                }
                unit.getImports().removeIf(i -> i.getNameAsString().equals("java.io.Serial") || i.getNameAsString().contains(".aot.")
                    || i.getNameAsString().equals("org.springframework.context.annotation.ImportRuntimeHints")
                    || i.getNameAsString().equals("org.springframework.lang.Contract")
                    || i.getNameAsString().endsWith("BeanFactoryInitializationAotProcessor"));
                unit.findAll(AnnotationExpr.class).stream()
                    .filter(a -> a.getNameAsString().equals("Serial") || a.getNameAsString().equals("java.io.Serial") || a.getNameAsString().equals("ImportRuntimeHints") || a.getNameAsString().equals("Contract"))
                    .forEach(a -> a.remove());
                unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(t -> t.getImplementedTypes().stream().anyMatch(i -> i.getNameAsString().equals("RuntimeHintsRegistrar")))
                    .forEach(t -> t.remove());
                unit.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).forEach(method -> {
                    if (method.isPrivate() && method.getParentNode().filter(parent -> parent instanceof ClassOrInterfaceDeclaration declaration && declaration.isInterface()).isPresent()) {
                        if (!method.isStatic()) throw new IllegalStateException("Private instance interface method requires a source port: " + path + "#" + method.getNameAsString());
                        method.setPrivate(false).setPublic(true);
                    }
                });
                unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(type -> type.getNameAsString().endsWith("AotProcessor"))
                    .forEach(type -> type.remove());
                unit.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).stream()
                    .filter(method -> method.getTypeAsString().endsWith("AotProcessor"))
                    .forEach(method -> method.remove());
                lowerResourceReads(unit);
                // Innermost records must be converted before their enclosing declaration.
                List<RecordDeclaration> records = new ArrayList<>(unit.findAll(RecordDeclaration.class));
                java.util.Collections.reverse(records);
                records.forEach(Java8SourceGenerator::lowerRecord);
                unit.findAll(ClassOrInterfaceDeclaration.class).forEach(type -> {
                    type.getModifiers().removeIf(m -> m.getKeyword() == Modifier.Keyword.SEALED
                        || m.getKeyword() == Modifier.Keyword.NON_SEALED);
                    type.getPermittedTypes().clear();
                });
                unit.findAll(NormalAnnotationExpr.class).stream()
                    .filter(a -> a.getNameAsString().equals("Deprecated") || a.getNameAsString().equals("java.lang.Deprecated"))
                    .forEach(a -> a.replace(new MarkerAnnotationExpr(a.getName().clone())));
                lowerPlatformFactories(unit);
                Path target = output.resolve(input.relativize(path));
                Files.createDirectories(target.getParent());
                String content = unit.toString();
                generated.add(target);
                if (!Files.exists(target) || !Files.readString(target).equals(content)) {
                    Files.writeString(target, content);
                }
            }
        }
        removeStale(output, generated);
    }

    private static String java8Namespace(String name) {
        if (name.startsWith("java.net.http.")) return "org.springframework.ai.compat.jdkhttp." + name.substring("java.net.http.".length());
        if (name.equals("java.util.concurrent.Flow") || name.startsWith("java.util.concurrent.Flow.")) return "org.springframework.ai.compat.jdkhttp.Flow" + name.substring("java.util.concurrent.Flow".length());
        for (String prefix : java.util.List.of("jakarta.servlet.", "jakarta.validation.", "jakarta.persistence.",
                "jakarta.transaction.", "jakarta.enterprise.", "jakarta.inject.", "jakarta.annotation.", "jakarta.websocket.", "jakarta.xml.bind.")) {
            if (name.startsWith(prefix)) return "javax." + name.substring("jakarta.".length());
        }
        return name;
    }

    private static void removeStale(Path output, java.util.Set<Path> generated) throws java.io.IOException {
        if (!Files.exists(output)) return;
        try (var paths = Files.walk(output)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java") && !generated.contains(p)).collect(Collectors.toList())) {
                Files.delete(path);
            }
        }
    }

    private static void lowerRecord(RecordDeclaration record) {
        ClassOrInterfaceDeclaration type = new ClassOrInterfaceDeclaration();
        type.setName(record.getNameAsString());
        type.setModifiers(record.getModifiers().stream().map(Modifier::clone).collect(Collectors.toCollection(NodeList::new)));
        type.setFinal(true);
        if (record.getParentNode().filter(p -> p instanceof TypeDeclaration).isPresent()) {
            type.setStatic(true);
        }
        type.setTypeParameters(record.getTypeParameters().stream().map(n -> n.clone()).collect(Collectors.toCollection(NodeList::new)));
        type.setImplementedTypes(record.getImplementedTypes().stream().map(n -> n.clone()).collect(Collectors.toCollection(NodeList::new)));
        type.setAnnotations(record.getAnnotations().stream().map(n -> n.clone()).collect(Collectors.toCollection(NodeList::new)));
        record.getComment().ifPresent(c -> type.setComment(c.clone()));
        if (type.getAnnotationByName("JsonAutoDetect").isEmpty()) {
            type.addAnnotation(StaticJavaParser.parseAnnotation("@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY, getterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE, isGetterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE, setterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)"));
        }
        List<Parameter> components = record.getParameters().stream().map(Parameter::clone).collect(Collectors.toList());
        CompactConstructorDeclaration compact = record.getMembers().stream()
            .filter(BodyDeclaration::isCompactConstructorDeclaration)
            .map(BodyDeclaration::asCompactConstructorDeclaration).findFirst().orElse(null);
        for (BodyDeclaration<?> member : record.getMembers()) {
            if (!(member instanceof CompactConstructorDeclaration)) {
                type.addMember(member.clone());
            }
        }
        for (Parameter component : components) {
            String name = component.getNameAsString();
            var fieldType = component.isVarArgs() ? new com.github.javaparser.ast.type.ArrayType(component.getType().clone()) : component.getType().clone();
            FieldDeclaration field = type.addField(fieldType, name, Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
            field.setAnnotations(componentAnnotations(component, ElementType.FIELD));
            if (type.getMethodsByName(name).stream().noneMatch(m -> m.getParameters().isEmpty())) {
                type.addMethod(name, Modifier.Keyword.PUBLIC).setType(fieldType.clone())
                    .setAnnotations(componentAnnotations(component, ElementType.METHOD))
                    .setBody(StaticJavaParser.parseBlock("{ return this." + name + "; }"));
            }
        }
        ConstructorDeclaration canonical = type.getConstructors().stream().filter(c -> canonical(c, components)).findFirst().orElse(null);
        if (canonical == null) {
            canonical = type.addConstructor();
            var access = compact != null && compact.getAccessSpecifier() != com.github.javaparser.ast.AccessSpecifier.NONE
                ? compact.getModifiers() : record.getModifiers();
            access.stream().filter(m -> m.getKeyword() == Modifier.Keyword.PUBLIC || m.getKeyword() == Modifier.Keyword.PROTECTED || m.getKeyword() == Modifier.Keyword.PRIVATE).forEach(m -> canonicalAccess(type, m.getKeyword()));
            if (record.getParentNode().filter(p -> p instanceof ClassOrInterfaceDeclaration c && c.isInterface()).isPresent()) {
                canonical.setPublic(true);
            }
            for (Parameter component : components) {
                Parameter parameter = component.clone();
                parameter.setAnnotations(componentAnnotations(component, ElementType.PARAMETER));
                canonical.addParameter(parameter);
            }
            BlockStmt body = compact == null ? new BlockStmt() : compact.getBody().clone();
            for (Parameter component : components) {
                body.addStatement("this." + component.getNameAsString() + " = " + component.getNameAsString() + ";");
            }
            canonical.setBody(body);
            if (compact != null) {
                canonical.setAnnotations(compact.getAnnotations().stream().map(n -> n.clone()).collect(Collectors.toCollection(NodeList::new)));
                compact.getComment().ifPresent(c -> type.getConstructors().get(type.getConstructors().size() - 1).setComment(c.clone()));
            }
        }
        type.addAnnotation("org.springframework.ai.util.Java8Record");
        // Records are implicit constructor-binding types in Boot 3. Their Java 8
        // replacements need the native Boot 2 constructor marker, even when declared
        // in a model-options file rather than a ConfigurationProperties source unit.
        if (canonical.getAnnotations().stream().noneMatch(annotation -> annotation.getNameAsString().endsWith("ConstructorBinding"))) {
            canonical.addAnnotation("org.springframework.boot.context.properties.ConstructorBinding");
        }
        boolean hasCreator = type.findAll(AnnotationExpr.class).stream().anyMatch(a -> a.getNameAsString().endsWith("JsonCreator"));
        if (!hasCreator) {
            canonical.addAnnotation("com.fasterxml.jackson.annotation.JsonCreator");
        }
        for (Parameter parameter : canonical.getParameters()) {
            var jsonProperty = parameter.getAnnotations().stream()
                .filter(annotation -> annotation.getName().getIdentifier().equals("JsonProperty")).findFirst();
            String parameterName = parameter.getNameAsString();
            if (jsonProperty.isPresent()) {
                AnnotationExpr annotation = jsonProperty.get();
                if (annotation instanceof NormalAnnotationExpr normal) {
                    var value = normal.getPairs().stream().filter(pair -> pair.getNameAsString().equals("value")).findFirst();
                    if (value.isEmpty()) {
                        normal.addPair("value", new StringLiteralExpr(parameterName));
                    }
                    else if (value.get().getValue() instanceof StringLiteralExpr literal && literal.asString().isEmpty()) {
                        value.get().setValue(new StringLiteralExpr(parameterName));
                    }
                }
                else if (annotation instanceof MarkerAnnotationExpr) {
                    annotation.replace(new SingleMemberAnnotationExpr(annotation.getName().clone(), new StringLiteralExpr(parameterName)));
                }
                else if (annotation instanceof SingleMemberAnnotationExpr single
                    && single.getMemberValue() instanceof StringLiteralExpr literal && literal.asString().isEmpty()) {
                    single.setMemberValue(new StringLiteralExpr(parameterName));
                }
            }
            else {
                boolean ignored = components.stream().filter(c -> c.getNameAsString().equals(parameterName))
                    .flatMap(c -> c.getAnnotations().stream()).anyMatch(Java8SourceGenerator::ignoredJsonProperty);
                if (ignored) {
                    parameter.addAnnotation(StaticJavaParser.parseAnnotation(
                        "@com.fasterxml.jackson.annotation.JsonProperty(value = " + new StringLiteralExpr(parameterName)
                        + ", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)"));
                }
                else {
                    parameter.addAnnotation(property(parameterName));
                }
            }
        }
        for (Parameter component : components) {
            type.getMethodsByName(component.getNameAsString()).stream()
                .filter(m -> m.getParameters().isEmpty())
                .forEach(m -> m.getAnnotations().removeIf(a -> a.getNameAsString().equals("Override")));
        }
        String name = type.getNameAsString();
        if (type.getMethodsByName("equals").stream().noneMatch(m -> m.getParameters().size() == 1 && m.getParameter(0).getType().asString().equals("Object"))) {
            String comparisons = components.stream().map(Java8SourceGenerator::equality).collect(Collectors.joining(" && "));
            type.addMember(StaticJavaParser.parseBodyDeclaration("@Override public boolean equals(Object other) { if (this == other) return true; if (other == null || getClass() != other.getClass()) return false; " + name + " that = (" + name + ") other; return " + (comparisons.isEmpty() ? "true" : comparisons) + "; }"));
        }
        if (type.getMethodsByName("hashCode").stream().noneMatch(m -> m.getParameters().isEmpty())) {
            StringBuilder body = new StringBuilder("@Override public int hashCode() { int result = 0;");
            for (Parameter component : components) {
                body.append("result = 31 * result + ").append(hash(component)).append(';');
            }
            type.addMember(StaticJavaParser.parseBodyDeclaration(body + "return result; }"));
        }
        if (type.getMethodsByName("toString").stream().noneMatch(m -> m.getParameters().isEmpty())) {
            StringBuilder body = new StringBuilder("@Override public String toString() { return \"").append(name).append("[");
            for (int i = 0; i < components.size(); i++) {
                Parameter component = components.get(i);
                if (i > 0) body.append(", ");
                body.append(component.getNameAsString()).append("=\" + java.lang.String.valueOf((Object) this.")
                    .append(component.getNameAsString()).append(") + \"");
            }
            type.addMember(StaticJavaParser.parseBodyDeclaration(body + "]\"; }"));
        }
        if (record.getParentNode().orElse(null) instanceof com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt local) {
            local.replace(new com.github.javaparser.ast.stmt.LocalClassDeclarationStmt(type));
        }
        else if (!record.replace(type)) {
            throw new IllegalStateException("Cannot replace record " + record.getNameAsString());
        }
    }

    private static boolean ignoredJsonProperty(AnnotationExpr annotation) {
        if (!annotation.getName().getIdentifier().equals("JsonIgnore")) return false;
        if (annotation instanceof SingleMemberAnnotationExpr single) return !single.getMemberValue().toString().equals("false");
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream().filter(p -> p.getNameAsString().equals("value"))
                .noneMatch(p -> p.getValue().toString().equals("false"));
        }
        return true;
    }

    private static NodeList<AnnotationExpr> componentAnnotations(Parameter component, ElementType site) {
        return component.getAnnotations().stream().filter(a -> {
            java.util.Set<ElementType> targets = targets(a);
            return targets.contains(site) || targets.contains(ElementType.TYPE_USE);
        }).map(AnnotationExpr::clone).collect(Collectors.toCollection(NodeList::new));
    }

    private static java.util.Set<ElementType> targets(AnnotationExpr annotation) {
        String simpleName = annotation.getNameAsString();
        String qualifiedName = simpleName;
        for (var imported : activeUnit.getImports()) {
            if (!imported.isAsterisk() && imported.getName().getIdentifier().equals(simpleName)) {
                qualifiedName = imported.getNameAsString();
                break;
            }
        }
        if (!qualifiedName.contains(".")) {
            qualifiedName = activeUnit.getPackageDeclaration().map(p -> p.getNameAsString() + ".").orElse("") + simpleName;
            if (simpleName.equals("Deprecated")) qualifiedName = "java.lang.Deprecated";
        }
        java.util.Set<ElementType> cached = annotationTargets.get(qualifiedName);
        if (cached != null) return cached;
        java.util.Set<ElementType> result = java.util.EnumSet.noneOf(ElementType.class);
        try {
            Class<?> type = Class.forName(qualifiedName, false, Java8SourceGenerator.class.getClassLoader());
            Target target = type.getAnnotation(Target.class);
            if (target != null) java.util.Collections.addAll(result, target.value());
            else java.util.Collections.addAll(result, ElementType.values());
        }
        catch (ClassNotFoundException | LinkageError unavailable) {
            // An annotation in this module may not have been compiled yet.
            Path source = activeInput.resolve(qualifiedName.replace('.', '/') + ".java");
            List<AnnotationDeclaration> declarations = activeUnit.findAll(AnnotationDeclaration.class);
            if (Files.isRegularFile(source)) {
                try { declarations = StaticJavaParser.parse(source).findAll(AnnotationDeclaration.class); }
                catch (java.io.IOException error) { throw new IllegalStateException("Cannot inspect annotation " + qualifiedName, error); }
            }
            AnnotationDeclaration declaration = declarations.stream()
                .filter(d -> d.getNameAsString().equals(simpleName)).findFirst().orElse(null);
            if (declaration != null && declaration.getAnnotationByName("Target").isPresent()) {
                String target = declaration.getAnnotationByName("Target").get().toString();
                for (ElementType site : ElementType.values()) {
                    if (java.util.regex.Pattern.compile("\\b" + site.name() + "\\b").matcher(target).find()) result.add(site);
                }
            }
            else {
                // Preserve unknown annotations and let javac validate their targets.
                java.util.Collections.addAll(result, ElementType.values());
            }
        }
        annotationTargets.put(qualifiedName, result);
        return result;
    }

    private static void canonicalAccess(ClassOrInterfaceDeclaration type, Modifier.Keyword keyword) {
        type.getConstructors().get(type.getConstructors().size() - 1).addModifier(keyword);
    }

    private static boolean canonical(ConstructorDeclaration constructor, List<Parameter> components) {
        if (constructor.getParameters().size() != components.size()) return false;
        for (int i = 0; i < components.size(); i++) {
            if (!constructor.getParameter(i).getType().equals(components.get(i).getType())) return false;
        }
        return true;
    }

    private static AnnotationExpr property(String name) {
        return new SingleMemberAnnotationExpr(new com.github.javaparser.ast.expr.Name("com.fasterxml.jackson.annotation.JsonProperty"), new StringLiteralExpr(name));
    }

    private static String equality(Parameter component) {
        String a = "this." + component.getNameAsString(), b = "that." + component.getNameAsString();
        if (!component.getType().isPrimitiveType() || component.isVarArgs()) return "java.util.Objects.equals(" + a + ", " + b + ")";
        PrimitiveType.Primitive primitive = component.getType().asPrimitiveType().getType();
        if (primitive == PrimitiveType.Primitive.DOUBLE) return "Double.compare(" + a + ", " + b + ") == 0";
        if (primitive == PrimitiveType.Primitive.FLOAT) return "Float.compare(" + a + ", " + b + ") == 0";
        return a + " == " + b;
    }

    private static String hash(Parameter component) {
        String value = "this." + component.getNameAsString();
        if (!component.getType().isPrimitiveType() || component.isVarArgs()) return "java.util.Objects.hashCode(" + value + ")";
        String wrapper = component.getType().asPrimitiveType().toBoxedType().asString();
        return wrapper + ".hashCode(" + value + ")";
    }

    private static void lowerResourceReads(CompilationUnit unit) {
        List<MethodCallExpr> calls = new ArrayList<>(unit.findAll(MethodCallExpr.class));
        java.util.Collections.reverse(calls);
        for (MethodCallExpr call : calls) {
            String name = call.getNameAsString();
            if (!java.util.Set.of("getContentAsByteArray", "getContentAsString", "stream", "toArray", "repeat", "isEmpty", "orTimeout").contains(name) || call.getScope().isEmpty()) continue;
            Expression receiver = call.getScope().get();
            if (receiver.toString().endsWith("Java8Platform")) continue;
            try {
                var type = receiver.calculateResolvedType();
                if (!type.isReferenceType()) continue;
                var reference = type.asReferenceType();
                String owner = reference.getQualifiedName();
                if ((name.equals("getContentAsByteArray") || name.equals("getContentAsString"))
                    && (owner.equals("org.springframework.core.io.Resource") || reference.getAllAncestors().stream().anyMatch(parent -> parent.getQualifiedName().equals("org.springframework.core.io.Resource")))) {
                    call.getArguments().addFirst(receiver.clone());
                    call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
                }
                else if (name.equals("orTimeout") && call.getArguments().size() == 2 && owner.equals("java.util.concurrent.CompletableFuture")) {
                    call.getArguments().addFirst(receiver.clone());
                    call.setScope(StaticJavaParser.parseExpression("com.alibaba.cloud.ai.graph.utils.FutureTimeouts"));
                }
                else if (name.equals("isEmpty") && call.getArguments().isEmpty() && owner.equals("java.util.Optional")) {
                    call.replace(StaticJavaParser.parseExpression("!(" + receiver + ").isPresent()"));
                }
                else if (name.equals("isEmpty") && call.getArguments().isEmpty()
                    && (owner.equals("java.lang.StringBuilder") || owner.equals("java.lang.StringBuffer") || owner.equals("java.lang.CharSequence"))) {
                    call.replace(StaticJavaParser.parseExpression("((" + receiver + ").length() == 0)"));
                }
                else if (name.equals("stream") && call.getArguments().isEmpty() && owner.equals("java.util.Optional")) {
                    call.setName("optionalStream");
                    call.addArgument(receiver.clone());
                    call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
                }
                else if (name.equals("repeat") && call.getArguments().size() == 1 && owner.equals("java.lang.String")) {
                    call.getArguments().addFirst(receiver.clone());
                    call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
                }
                else if (name.equals("toArray") && call.getArguments().size() == 1 && call.getArgument(0).isMethodReferenceExpr()
                    && (owner.equals("java.util.Collection") || reference.getAllAncestors().stream().anyMatch(parent -> parent.getQualifiedName().equals("java.util.Collection")))) {
                    MethodReferenceExpr factory = call.getArgument(0).asMethodReferenceExpr();
                    String array = factory.getScope().toString();
                    if (factory.getIdentifier().equals("new") && array.endsWith("[]")) {
                        int dimension = array.indexOf("[]");
                        call.setArgument(0, StaticJavaParser.parseExpression("new " + array.substring(0, dimension) + "[0]" + array.substring(dimension + 2)));
                    }
                }
            }
            catch (RuntimeException unresolved) {
                // Unresolved receivers stay unchanged and are diagnosed by javac. In
                // particular, servlet response reads and Stream.toArray must retain
                // their native Java 8 overloads rather than matching by name alone.
            }
        }
    }

    private static void lowerPlatformFactories(CompilationUnit unit) {
        for (MethodReferenceExpr reference : unit.findAll(MethodReferenceExpr.class)) {
            String owner = reference.getScope().toString().replace("java.util.", "");
            if ((owner.equals("List") || owner.equals("Set") || owner.equals("Map")) && reference.getIdentifier().equals("of")) {
                reference.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Collections"));
                reference.setIdentifier(owner.toLowerCase() + "Of");
            }
            if ((owner.equals("List") || owner.equals("Set") || owner.equals("Map")) && reference.getIdentifier().equals("copyOf")) {
                reference.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Collections"));
                reference.setIdentifier(owner.toLowerCase() + "CopyOf");
            }
            if ((owner.equals("Optional") || owner.equals("java.util.Optional")) && reference.getIdentifier().equals("stream")) {
                reference.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
                reference.setIdentifier("optionalStream");
            }
            if ((owner.equals("String") || owner.equals("java.lang.String"))
                && java.util.Set.of("strip", "stripLeading", "stripTrailing", "isBlank", "repeat").contains(reference.getIdentifier())) {
                reference.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
            }
        }
        List<MethodCallExpr> calls = new ArrayList<>(unit.findAll(MethodCallExpr.class));
        java.util.Collections.reverse(calls);
        for (MethodCallExpr call : calls) {
            String scope = call.getScope().map(expression -> expression.isNameExpr() ? expression.asNameExpr().getNameAsString() : expression.toString()).orElse("");
            String name = call.getNameAsString();
            if ((scope.equals("Collectors") || scope.equals("java.util.stream.Collectors")) && name.startsWith("toUnmodifiable")) {
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Collections"));
            }
            else if ((scope.equals("CompletableFuture") || scope.equals("java.util.concurrent.CompletableFuture")) && name.equals("failedFuture")
                || (scope.equals("Predicate") || scope.equals("java.util.function.Predicate")) && name.equals("not")) {
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
            }
            else if (name.equals("readAllBytes") && call.getArguments().isEmpty() && call.getScope().isPresent()) {
                call.getArguments().add(call.getScope().get().clone());
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
            }
            else if ((scope.equals("List") || scope.equals("java.util.List") || scope.equals("Set") || scope.equals("java.util.Set") || scope.equals("Map") || scope.equals("java.util.Map"))
                && (name.equals("of") || name.equals("copyOf") || name.equals("ofEntries") || name.equals("entry"))) {
                String kind = scope.substring(scope.lastIndexOf('.') + 1);
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Collections"));
                call.setName(kind.toLowerCase() + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            }
            else if ((scope.equals("Path") || scope.equals("java.nio.file.Path")) && name.equals("of")) {
                call.setScope(StaticJavaParser.parseExpression("java.nio.file.Paths"));
                call.setName("get");
            }
            else if ((scope.equals("Files") || scope.equals("java.nio.file.Files")) && (name.equals("writeString") || name.equals("readString"))) {
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
            }
            else if (name.equals("formatted") && call.getScope().isPresent() && !scope.endsWith("Java8Platform")) {
                call.getArguments().addFirst(call.getScope().get().clone());
                call.setScope(StaticJavaParser.parseExpression("java.lang.String"));
                call.setName("format");
            }
            else if (((((name.equals("isBlank") || name.equals("strip") || name.equals("stripLeading") || name.equals("stripTrailing")) && call.getArguments().isEmpty()
                || name.equals("repeat") && call.getArguments().size() == 1 && call.getScope().filter(Expression::isStringLiteralExpr).isPresent())
                && !scope.contains("assertThat(") && !scope.endsWith("StringUtils"))) && call.getScope().isPresent() && !scope.endsWith("Java8Platform")) {
                call.getArguments().addFirst(call.getScope().get().clone());
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
            }
            else if (scope.equals("Objects") && (name.equals("requireNonNullElse") || name.equals("requireNonNullElseGet"))) {
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Platform"));
            }
            else if (name.equals("orElseThrow") && call.getArguments().isEmpty()) {
                call.addArgument(StaticJavaParser.parseExpression("java.util.NoSuchElementException::new"));
            }
            else if (name.equals("toList") && call.getArguments().isEmpty() && !scope.isEmpty() && !scope.endsWith("Collectors")) {
                // Stream.toList permits nulls and returns an unmodifiable list.
                Expression stream = call.getScope().get().clone();
                call.setScope(StaticJavaParser.parseExpression("org.springframework.ai.util.Java8Collections"));
                call.setName("streamToList");
                call.addArgument(stream);
            }
        }
    }
}
