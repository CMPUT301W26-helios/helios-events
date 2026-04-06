
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Android UML extractor for pure-Java Android Studio projects.
 * MADE WITH SIGNIFICANT LLM ASSISTANCE FROM GPT 5.4 + CLAUDE
 * <p>Goals:
 * <ul>
 *   <li>Use a real Java parser (JDK compiler AST), not regex.</li>
 *   <li>Stay small and dependency-light: JDK only.</li>
 *   <li>Emit project-focused UML, filtering Android / library clutter out of nodes.</li>
 *   <li>Generate multiple PlantUML views for readability plus a plaintext audit report.</li>
 * </ul>
 */
public final class AndroidUmlExtractor {
    private static final Set<String> DEFAULT_EXTERNAL_PREFIXES = Set.of(
            "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.", "com.google.", "org.jetbrains."
    );
    private static final Set<String> COLLECTION_LIKE = Set.of(
            "Collection", "List", "ArrayList", "LinkedList", "Set", "HashSet", "LinkedHashSet",
            "Iterable", "Map", "HashMap", "LinkedHashMap", "SparseArray"
    );
    private static final Set<String> NON_NULL_ANNOTATIONS = Set.of(
            "NonNull", "NotNull", "Nonnull", "RecentlyNonNull"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$.]*");
    private static final Pattern CAMEL_SPLIT = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final Map<String, String> LAYER_COLORS = createLayerColors();

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        UmlProject project = new UmlProject(config.projectRoot, config.module, config.variant);

        ProjectFiles files = discoverProjectFiles(config, project);
        parseJava(files.javaFiles, project);
        inferRelations(project);
        parseManifest(files.manifestPath, project);
        parseNavigation(files.navigationXmls, project);
        writeOutputs(config.outDir, project);

        System.out.println("Wrote UML outputs to: " + config.outDir.toAbsolutePath());
    }

    private static Map<String, String> createLayerColors() {
        LinkedHashMap<String, String> colors = new LinkedHashMap<>();
        colors.put("ui", "#D6EAF8");
        colors.put("viewmodel", "#E8DAEF");
        colors.put("service", "#D5F5E3");
        colors.put("repository", "#FAD7A0");
        colors.put("auth", "#E8DAEF");
        colors.put("model", "#FCF3CF");
        colors.put("utility", "#EAECEE");
        colors.put("controller", "#F5CBA7");
        colors.put("other", "#F8F9F9");
        return Collections.unmodifiableMap(colors);
    }

    private static ProjectFiles discoverProjectFiles(Config config, UmlProject project) throws IOException {
        Path moduleRoot = config.projectRoot.resolve(config.module).normalize();
        if (!Files.isDirectory(moduleRoot)) {
            throw new IllegalArgumentException("Module directory not found: " + moduleRoot);
        }

        LinkedHashSet<String> sourceSets = computeSourceSetCandidates(config.variant);
        List<Path> javaRoots = new ArrayList<>();
        for (String sourceSet : sourceSets) {
            Path javaRoot = moduleRoot.resolve(Path.of("src", sourceSet, "java"));
            if (Files.isDirectory(javaRoot)) {
                javaRoots.add(javaRoot);
            }
        }

        if (javaRoots.isEmpty()) {
            Path srcRoot = moduleRoot.resolve("src");
            if (Files.isDirectory(srcRoot)) {
                try (Stream<Path> stream = Files.walk(srcRoot)) {
                    stream.filter(Files::isDirectory)
                            .filter(path -> path.getFileName() != null && path.getFileName().toString().equals("java"))
                            .forEach(javaRoots::add);
                }
            }
        }

        List<Path> collectedJavaFiles = new ArrayList<>();
        for (Path javaRoot : javaRoots) {
            Files.walkFileTree(javaRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String normalized = dir.toString().replace('\\', '/');
                    if (normalized.contains("/build/") || normalized.endsWith("/build")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (normalized.contains("/src/test/") || normalized.endsWith("/src/test")
                            || normalized.contains("/src/androidTest/") || normalized.endsWith("/src/androidTest")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        collectedJavaFiles.add(file.normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        List<Path> javaFiles = collectedJavaFiles.stream().distinct().sorted().toList();

        Path manifestPath = findMergedManifest(moduleRoot, config.variant);
        if (manifestPath == null) {
            manifestPath = findFallbackManifest(moduleRoot, sourceSets);
        }
        if (manifestPath == null) {
            project.warnings.add("No manifest found. Checked merged manifests and src/<sourceSet>/AndroidManifest.xml.");
        }

        List<Path> navigationXmls = new ArrayList<>();
        for (String sourceSet : sourceSets) {
            Path navRoot = moduleRoot.resolve(Path.of("src", sourceSet, "res", "navigation"));
            if (Files.isDirectory(navRoot)) {
                try (Stream<Path> stream = Files.walk(navRoot)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".xml"))
                            .sorted()
                            .forEach(navigationXmls::add);
                }
            }
        }
        navigationXmls = navigationXmls.stream().distinct().sorted().toList();

        if (javaFiles.isEmpty()) {
            project.warnings.add("No Java files found for analysis.");
        }
        if (javaRoots.isEmpty()) {
            project.warnings.add("No Java source roots found under module src/.");
        }

        return new ProjectFiles(moduleRoot, javaFiles, manifestPath, navigationXmls, javaRoots);
    }

    private static LinkedHashSet<String> computeSourceSetCandidates(String variant) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String safeVariant = (variant == null || variant.isBlank()) ? "debug" : variant;
        result.add("main");
        result.add(safeVariant);

        List<String> tokens = splitVariantTokens(safeVariant);
        for (String token : tokens) {
            result.add(token);
        }

        StringBuilder cumulative = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i == 0) {
                cumulative.append(tokens.get(i));
            } else {
                cumulative.append(capitalize(tokens.get(i)));
            }
            result.add(cumulative.toString());
        }

        String buildType = tokens.isEmpty() ? safeVariant : tokens.get(tokens.size() - 1);
        result.add(buildType);

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (result.contains("main")) {
            ordered.add("main");
        }
        if (result.contains(safeVariant)) {
            ordered.add(safeVariant);
        }
        for (String value : result) {
            ordered.add(value);
        }
        return ordered;
    }

    private static List<String> splitVariantTokens(String variant) {
        if (variant == null || variant.isBlank()) {
            return List.of();
        }
        return Arrays.stream(CAMEL_SPLIT.split(variant))
                .filter(token -> !token.isBlank())
                .map(token -> Character.toLowerCase(token.charAt(0)) + token.substring(1))
                .toList();
    }

    private static String capitalize(String value) {
        return value == null || value.isBlank() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Path findMergedManifest(Path moduleRoot, String variant) throws IOException {
        List<Path> searchRoots = List.of(
                moduleRoot.resolve(Path.of("build", "intermediates", "merged_manifests")),
                moduleRoot.resolve(Path.of("build", "intermediates", "packaged_manifests"))
        );
        String needle = variant == null ? "" : variant.toLowerCase(Locale.ROOT);

        List<Path> candidates = new ArrayList<>();
        for (Path root : searchRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName() != null && path.getFileName().toString().equals("AndroidManifest.xml"))
                        .filter(path -> needle.isBlank() || path.toString().toLowerCase(Locale.ROOT).contains(needle))
                        .forEach(candidates::add);
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt((Path path) -> path.getNameCount()).thenComparing(Path::toString))
                .findFirst()
                .orElse(null);
    }

    private static Path findFallbackManifest(Path moduleRoot, LinkedHashSet<String> sourceSets) {
        List<String> order = new ArrayList<>(sourceSets);
        order.remove("main");
        order.add("main");
        for (String sourceSet : order) {
            Path manifest = moduleRoot.resolve(Path.of("src", sourceSet, "AndroidManifest.xml"));
            if (Files.isRegularFile(manifest)) {
                return manifest;
            }
        }
        return null;
    }

    private static void parseJava(List<Path> javaFiles, UmlProject project) throws IOException {
        if (javaFiles.isEmpty()) {
            return;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available. Run this with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(javaFiles);
            List<String> options = List.of("-proc:none", "-implicit:none", "-Xlint:none");
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, options, null, units);

            List<CompilationUnitTree> parsedUnits = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                parsedUnits.add(unit);
            }

            try {
                task.analyze();
            } catch (Throwable ignored) {
                project.warnings.add("Compiler semantic analysis reported errors; continuing with partial symbol resolution.");
            }

            Trees trees = Trees.instance(task);
            for (CompilationUnitTree unit : parsedUnits) {
                new AstCollector(project, unit, trees).scan(unit, null);
            }
        }

        long errorCount = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .count();
        if (errorCount > 0) {
            project.warnings.add("Compiler reported " + errorCount + " parse/analysis diagnostics. External Android symbols can cause this; project-owned types are still extracted when possible.");
        }
    }

    private static void inferRelations(UmlProject project) {
        Map<String, Set<String>> simpleToFqcn = new HashMap<>();
        for (UmlType type : project.types.values()) {
            simpleToFqcn.computeIfAbsent(type.simpleName, ignored -> new LinkedHashSet<>()).add(type.fqcn);
            simpleToFqcn.computeIfAbsent(type.displayName(), ignored -> new LinkedHashSet<>()).add(type.fqcn);
        }

        for (UmlType type : project.types.values()) {
            Optional<String> superTarget = resolveProjectType(type.superClassRaw, type.superClassHint, type.packageName, type.imports, simpleToFqcn, project);
            superTarget.filter(target -> !Objects.equals(target, type.fqcn))
                    .ifPresent(target -> project.addRelation(new UmlRelation(
                            RelationKind.GENERALIZATION, type.fqcn, target, "", "", "extends", "java", "high"
                    )));

            for (int i = 0; i < type.interfacesRaw.size(); i++) {
                String raw = type.interfacesRaw.get(i);
                String hint = i < type.interfaceHints.size() ? type.interfaceHints.get(i) : null;
                resolveProjectType(raw, hint, type.packageName, type.imports, simpleToFqcn, project)
                        .filter(target -> !Objects.equals(target, type.fqcn))
                        .ifPresent(target -> project.addRelation(new UmlRelation(
                                RelationKind.REALIZATION, type.fqcn, target, "", "", "implements", "java", "high"
                        )));
            }

            for (UmlField field : type.fields) {
                Optional<String> target = resolveProjectType(field.typeRaw, field.targetHint, type.packageName, type.imports, simpleToFqcn, project);
                if (target.isEmpty() || Objects.equals(target.get(), type.fqcn)) {
                    continue;
                }

                RelationKind kind = inferOwnershipKind(field);
                String toMultiplicity = inferMultiplicity(type, field);
                String confidence = kind == RelationKind.COMPOSITION ? "medium" : "medium";
                project.addRelation(new UmlRelation(
                        kind,
                        type.fqcn,
                        target.get(),
                        "1",
                        toMultiplicity,
                        field.name,
                        "field",
                        confidence
                ));
            }

            for (UmlMethod method : type.methods) {
                addMethodDependency(project, type, method, method.returnTypeRaw, method.returnTargetHint, simpleToFqcn, " returns", "method-return", "medium");
                for (UmlTypeUse param : method.parameterTypes) {
                    addMethodDependency(project, type, method, param.raw, param.targetHint, simpleToFqcn, " uses", "method-param", "medium");
                }
                for (UmlTypeUse thrown : method.thrownTypes) {
                    addMethodDependency(project, type, method, thrown.raw, thrown.targetHint, simpleToFqcn, " throws", "method-throws", "medium");
                }
                for (UmlTypeUse local : method.localTypeUses) {
                    addMethodDependency(project, type, method, local.raw, local.targetHint, simpleToFqcn, " uses", "method-local", "low");
                }
                for (UmlTypeUse created : method.newTypeUses) {
                    addMethodDependency(project, type, method, created.raw, created.targetHint, simpleToFqcn, " creates", "method-new", "low");
                }
            }
        }
    }

    private static void addMethodDependency(
            UmlProject project,
            UmlType owner,
            UmlMethod method,
            String raw,
            String hint,
            Map<String, Set<String>> simpleToFqcn,
            String suffix,
            String evidence,
            String confidence
    ) {
        Optional<String> target = resolveProjectType(raw, hint, owner.packageName, owner.imports, simpleToFqcn, project);
        target.filter(fqcn -> !Objects.equals(fqcn, owner.fqcn))
                .ifPresent(fqcn -> project.addRelation(new UmlRelation(
                        RelationKind.DEPENDENCY,
                        owner.fqcn,
                        fqcn,
                        "",
                        "",
                        method.name + "()" + suffix,
                        evidence,
                        confidence
                )));
    }

    private static Optional<String> resolveProjectType(
            String rawType,
            String targetHint,
            String packageName,
            List<String> imports,
            Map<String, Set<String>> simpleToFqcn,
            UmlProject project
    ) {
        if (targetHint != null && !targetHint.isBlank()) {
            if (project.types.containsKey(targetHint)) {
                return Optional.of(targetHint);
            }
            String normalizedHint = targetHint.replace('$', '.');
            if (project.types.containsKey(normalizedHint)) {
                return Optional.of(normalizedHint);
            }
        }

        if (rawType == null || rawType.isBlank()) {
            return Optional.empty();
        }

        TypeRef ref = TypeRef.parse(rawType);
        List<String> candidates = new ArrayList<>();
        if (ref.target != null) {
            candidates.add(ref.target);
        }
        if (targetHint != null && !targetHint.isBlank()) {
            candidates.add(targetHint);
        }

        for (String candidate : candidates) {
            String normalized = candidate.replace('$', '.');
            if (normalized.contains(".")) {
                if (project.types.containsKey(normalized) && !isExternalType(normalized)) {
                    return Optional.of(normalized);
                }
                String simple = simpleName(normalized);
                Set<String> fqcns = simpleToFqcn.getOrDefault(simple, Set.of());
                if (fqcns.size() == 1) {
                    return Optional.of(fqcns.iterator().next());
                }
            }

            String samePackage = packageName == null || packageName.isBlank() ? normalized : packageName + "." + normalized;
            if (project.types.containsKey(samePackage)) {
                return Optional.of(samePackage);
            }

            for (String imported : imports) {
                if (imported.endsWith("." + normalized) && project.types.containsKey(imported)) {
                    return Optional.of(imported);
                }
                if (imported.endsWith(".*")) {
                    String wildcard = imported.substring(0, imported.length() - 2) + "." + normalized;
                    if (project.types.containsKey(wildcard)) {
                        return Optional.of(wildcard);
                    }
                }
            }

            Set<String> fqcns = simpleToFqcn.getOrDefault(normalized, Set.of());
            if (fqcns.size() == 1) {
                return Optional.of(fqcns.iterator().next());
            }
        }

        return Optional.empty();
    }

    private static RelationKind inferOwnershipKind(UmlField field) {
        boolean privateField = "-".equals(field.visibility);
        boolean directOwnedInstance = !field.collectionLike
                && !field.optionalLike
                && !field.array
                && (field.initializedAtDeclarationWithNew || field.constructorsAssigningNewCount > 0)
                && !field.assignedFromConstructorParam
                && !field.assignedNull;
        return (privateField && directOwnedInstance) ? RelationKind.COMPOSITION : RelationKind.ASSOCIATION;
    }

    private static String inferMultiplicity(UmlType owner, UmlField field) {
        if (field.array || field.collectionLike) {
            return "0..*";
        }
        if (field.optionalLike) {
            return "0..1";
        }
        if (field.nonNullAnnotated) {
            return "1";
        }
        if (field.isFinal && field.initializedAtDeclarationNonNull && !field.assignedNull) {
            return "1";
        }
        int nonNullCtorAssignments = field.constructorsAssigningNewCount + field.constructorsAssigningParamCount;
        if (field.isFinal && owner.constructorCount > 0 && nonNullCtorAssignments >= owner.constructorCount && !field.assignedNull) {
            return "1";
        }
        return "0..1";
    }

    private static void parseManifest(Path manifestPath, UmlProject project) {
        if (manifestPath == null || !Files.exists(manifestPath)) {
            return;
        }
        try {
            Document doc = buildXml(manifestPath);
            org.w3c.dom.Element root = doc.getDocumentElement();
            String manifestPackage = root.getAttribute("package");
            org.w3c.dom.Element application = firstChild(root, "application");
            if (application != null) {
                String appName = attr(application, "android:name", "name");
                resolveManifestClass(appName, manifestPackage)
                        .filter(project.types::containsKey)
                        .ifPresent(fqcn -> project.types.get(fqcn).stereotypes.add("application"));
            }

            stereotypeManifestComponents(root, manifestPackage, project, "activity", "activity");
            stereotypeManifestComponents(root, manifestPackage, project, "service", "service");
            stereotypeManifestComponents(root, manifestPackage, project, "receiver", "receiver");
            stereotypeManifestComponents(root, manifestPackage, project, "provider", "provider");
        } catch (Exception e) {
            project.warnings.add("Manifest parse failed: " + e.getMessage());
        }
    }

    private static void stereotypeManifestComponents(org.w3c.dom.Element root, String manifestPackage, UmlProject project, String tag, String stereotype) {
        NodeList nodes = root.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) nodes.item(i);
            String name = attr(element, "android:name", "name");
            resolveManifestClass(name, manifestPackage)
                    .filter(project.types::containsKey)
                    .ifPresent(fqcn -> project.types.get(fqcn).stereotypes.add(stereotype));
        }
    }

    private static void parseNavigation(List<Path> navigationXmls, UmlProject project) {
        for (Path navPath : navigationXmls) {
            try {
                Document doc = buildXml(navPath);
                org.w3c.dom.Element root = doc.getDocumentElement();
                Map<String, String> destinationIdToClass = new HashMap<>();
                collectNavDestinations(root, destinationIdToClass, project);
                collectNavActions(root, destinationIdToClass, project);
            } catch (Exception e) {
                project.warnings.add("Navigation parse failed for " + navPath + ": " + e.getMessage());
            }
        }
    }

    private static void collectNavDestinations(org.w3c.dom.Element root, Map<String, String> destinationIdToClass, UmlProject project) {
        walkElements(root, element -> {
            String tag = element.getTagName();
            if (!(tag.equals("fragment") || tag.equals("activity") || tag.equals("dialog") || tag.equals("navigation"))) {
                return;
            }
            String id = normalizeId(attr(element, "android:id", "id"));
            String name = attr(element, "android:name", "name", "class");
            if (name == null || name.isBlank()) {
                return;
            }
            String fqcn = resolveNavClass(name, project);
            if (fqcn == null) {
                return;
            }
            if (id != null) {
                destinationIdToClass.put(id, fqcn);
            }
            UmlType type = project.types.get(fqcn);
            if (type != null && !tag.equals("navigation")) {
                type.stereotypes.add(tag);
            }
        });
    }

    private static void collectNavActions(org.w3c.dom.Element root, Map<String, String> destinationIdToClass, UmlProject project) {
        walkElements(root, element -> {
            String tag = element.getTagName();
            if (!(tag.equals("fragment") || tag.equals("activity") || tag.equals("dialog"))) {
                return;
            }
            String fromName = attr(element, "android:name", "name", "class");
            if (fromName == null || fromName.isBlank()) {
                return;
            }
            String fromFqcn = resolveNavClass(fromName, project);
            if (fromFqcn == null) {
                return;
            }

            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (!(child instanceof org.w3c.dom.Element childElement)) {
                    continue;
                }
                if (!childElement.getTagName().equals("action")) {
                    continue;
                }
                String destination = normalizeId(attr(childElement, "app:destination", "destination"));
                String toFqcn = destinationIdToClass.get(destination);
                if (toFqcn != null && !Objects.equals(fromFqcn, toFqcn)) {
                    project.addRelation(new UmlRelation(
                            RelationKind.NAVIGATION,
                            fromFqcn,
                            toFqcn,
                            "",
                            "",
                            blankToDash(normalizeId(attr(childElement, "android:id", "id"))),
                            "navigation",
                            "high"
                    ));
                }
            }
        });
    }

    private static Document buildXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = Files.newInputStream(path)) {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    private static Optional<String> resolveManifestClass(String name, String manifestPackage) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (name.startsWith(".")) {
            return Optional.of(manifestPackage + name);
        }
        return Optional.of(name.contains(".") ? name : manifestPackage + "." + name);
    }

    private static String resolveNavClass(String name, UmlProject project) {
        if (project.types.containsKey(name)) {
            return name;
        }
        String simple = simpleName(name);
        List<String> matches = project.types.keySet().stream()
                .filter(fqcn -> fqcn.endsWith("." + simple) || fqcn.equals(simple))
                .sorted()
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        int slash = id.indexOf('/');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }

    private static void writeOutputs(Path outDir, UmlProject project) throws IOException {
        Files.createDirectories(outDir);
        writeReferenceText(outDir.resolve("uml_reference.txt"), project);
        writePlantUml(outDir.resolve("overview.puml"), project, DiagramView.overview());
        writePlantUml(outDir.resolve("domain_model.puml"), project, DiagramView.domainModel());
        writePlantUml(outDir.resolve("service_data.puml"), project, DiagramView.serviceData());
        writePlantUml(outDir.resolve("ui_structure.puml"), project, DiagramView.uiStructure());
        writePlantUml(outDir.resolve("screen_navigation.puml"), project, DiagramView.screenNavigation());
        writePlantUml(outDir.resolve("full_reference.puml"), project, DiagramView.fullReference());
        writePackageDependencyPlantUml(outDir.resolve("package_dependencies.puml"), project);
    }

    private static void writeReferenceText(Path outFile, UmlProject project) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(outFile, StandardCharsets.UTF_8))) {
            out.println("Android UML Extractor Report");
            out.println("Generated: " + Instant.now());
            out.println("Project root: " + project.projectRoot);
            out.println("Module: " + project.module);
            out.println("Variant: " + project.variant);
            out.println();

            out.println("Generated outputs");
            out.println("=================");
            out.println("- overview.puml            : architecture overview, compact structural relations");
            out.println("- domain_model.puml        : model-focused diagram with multiplicity");
            out.println("- service_data.puml        : service/data/auth classes with immediate collaborators");
            out.println("- ui_structure.puml        : UI classes and structural/nav relations");
            out.println("- screen_navigation.puml   : navigation-only view between screens");
            out.println("- full_reference.puml      : detailed reference view with compacted edges");
            out.println("- package_dependencies.puml: package-level dependency map");
            out.println("- uml_reference.txt        : plaintext audit trail");
            out.println();

            if (!project.warnings.isEmpty()) {
                out.println("Warnings");
                out.println("========");
                for (String warning : project.warnings) {
                    out.println("- " + warning);
                }
                out.println();
            }

            out.println("Counts");
            out.println("======");
            out.println("Types: " + project.types.size());
            out.println("Packages: " + project.types.values().stream().map(type -> type.packageName).filter(pkg -> !pkg.isBlank()).distinct().count());
            Map<RelationKind, Long> relationCounts = project.relations.values().stream()
                    .collect(Collectors.groupingBy(relation -> relation.kind, () -> new EnumMap<>(RelationKind.class), Collectors.counting()));
            for (RelationKind kind : RelationKind.values()) {
                out.println("- " + kind + ": " + relationCounts.getOrDefault(kind, 0L));
            }
            out.println();

            out.println("Packages");
            out.println("========");
            Map<String, Long> packageCounts = project.types.values().stream()
                    .collect(Collectors.groupingBy(type -> type.packageName, TreeMap::new, Collectors.counting()));
            for (Map.Entry<String, Long> entry : packageCounts.entrySet()) {
                out.println("- " + entry.getKey() + " (" + entry.getValue() + ")");
            }
            out.println();

            out.println("Types");
            out.println("=====");
            for (UmlType type : project.sortedTypes()) {
                out.println(type.fqcn + " [" + type.kind + "]" + formatStereotypes(type.stereotypes) + " layer=" + classifyLayer(type));
                out.println("  file: " + type.sourceFile);
                if (type.superClassRaw != null) {
                    out.println("  extends: " + type.superClassRaw);
                }
                if (!type.interfacesRaw.isEmpty()) {
                    out.println("  implements: " + String.join(", ", type.interfacesRaw));
                }
                if (!type.fields.isEmpty()) {
                    out.println("  fields:");
                    for (UmlField field : type.fields.stream().sorted(Comparator.comparing(f -> f.name)).toList()) {
                        out.printf("    %s %s: %s | multiplicity=%s | relation=%s | hints=%s%n",
                                field.visibility,
                                field.name,
                                field.typeRaw,
                                inferMultiplicity(type, field),
                                inferOwnershipKind(field),
                                fieldHintSummary(field));
                    }
                }
                if (!type.methods.isEmpty()) {
                    out.println("  methods:");
                    for (UmlMethod method : type.methods.stream().sorted(Comparator.comparing(m -> m.name)).toList()) {
                        String returnText = method.isConstructor ? "<ctor>" : method.returnTypeRaw;
                        out.printf("    %s %s(%s): %s%n",
                                method.visibility,
                                method.name,
                                method.parameterTypes.stream().map(typeUse -> typeUse.raw).collect(Collectors.joining(", ")),
                                returnText);
                    }
                }

                List<UmlRelation> outgoing = project.sortedRelations().stream()
                        .filter(relation -> relation.from.equals(type.fqcn))
                        .toList();
                List<UmlRelation> incoming = project.sortedRelations().stream()
                        .filter(relation -> relation.to.equals(type.fqcn))
                        .toList();
                if (!outgoing.isEmpty()) {
                    out.println("  outgoing relations:");
                    for (UmlRelation relation : outgoing) {
                        out.printf("    -> [%s] %s %s/%s label=%s evidence=%s confidence=%s%n",
                                relation.kind,
                                relation.to,
                                blankToDash(relation.fromMultiplicity),
                                blankToDash(relation.toMultiplicity),
                                blankToDash(relation.label),
                                relation.evidence,
                                relation.confidence);
                    }
                }
                if (!incoming.isEmpty()) {
                    out.println("  incoming relations:");
                    for (UmlRelation relation : incoming) {
                        out.printf("    <- [%s] %s %s/%s label=%s evidence=%s confidence=%s%n",
                                relation.kind,
                                relation.from,
                                blankToDash(relation.fromMultiplicity),
                                blankToDash(relation.toMultiplicity),
                                blankToDash(relation.label),
                                relation.evidence,
                                relation.confidence);
                    }
                }
                out.println();
            }

            out.println("Raw relations");
            out.println("=============");
            for (UmlRelation relation : project.sortedRelations()) {
                out.printf("%s --[%s %s/%s]--> %s | label=%s | evidence=%s | confidence=%s%n",
                        relation.from,
                        relation.kind,
                        blankToDash(relation.fromMultiplicity),
                        blankToDash(relation.toMultiplicity),
                        relation.to,
                        blankToDash(relation.label),
                        relation.evidence,
                        relation.confidence);
            }
        }
    }

    private static String fieldHintSummary(UmlField field) {
        List<String> hints = new ArrayList<>();
        if (field.nonNullAnnotated) {
            hints.add("@NonNull");
        }
        if (field.isFinal) {
            hints.add("final");
        }
        if (field.initializedAtDeclarationWithNew) {
            hints.add("decl-new");
        }
        if (field.constructorsAssigningNewCount > 0) {
            hints.add("ctor-new=" + field.constructorsAssigningNewCount);
        }
        if (field.constructorsAssigningParamCount > 0) {
            hints.add("ctor-param=" + field.constructorsAssigningParamCount);
        }
        if (field.assignedNull) {
            hints.add("null-assigned");
        }
        return hints.isEmpty() ? "-" : String.join(", ", hints);
    }

    private static void writePlantUml(Path outFile, UmlProject project, DiagramView view) throws IOException {
        Set<String> includedTypeIds = selectTypesForView(project, view);
        Map<String, UmlType> includedTypes = project.sortedTypes().stream()
                .filter(type -> includedTypeIds.contains(type.fqcn))
                .collect(Collectors.toMap(type -> type.fqcn, type -> type, (a, b) -> a, LinkedHashMap::new));
        Map<String, String> aliases = new LinkedHashMap<>();
        int i = 1;
        for (UmlType type : includedTypes.values()) {
            aliases.put(type.fqcn, "T" + i++);
        }
        List<UmlRelation> relations = selectRelationsForView(project, includedTypeIds, view);

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(outFile, StandardCharsets.UTF_8))) {
            out.println("@startuml");
            out.println(view.direction);
            emitCommonSkinParams(out);
            if (!view.showFields && !view.showMethods) {
                out.println("hide fields");
                out.println("hide methods");
            } else {
                out.println("hide empty members");
                if (!view.showFields) {
                    out.println("hide fields");
                }
                if (!view.showMethods) {
                    out.println("hide methods");
                }
            }
            out.println();
            out.println("title " + escapePlantUml(view.title));
            out.println();
            emitLegend(out);

            if (includedTypes.isEmpty()) {
                out.println("note \"No matching project-owned types for this view.\" as N1");
                out.println("@enduml");
                return;
            }

            Map<String, List<UmlType>> byPackage = includedTypes.values().stream()
                    .collect(Collectors.groupingBy(type -> type.packageName == null ? "" : type.packageName, TreeMap::new, Collectors.toList()));

            for (Map.Entry<String, List<UmlType>> entry : byPackage.entrySet()) {
                String pkg = entry.getKey();
                if (!pkg.isBlank()) {
                    out.println("package \"" + escapePlantUml(pkg) + "\" {");
                }
                List<UmlType> typesInPackage = new ArrayList<>(entry.getValue());
                typesInPackage.sort(Comparator.comparing(UmlType::displayName));
                for (UmlType type : typesInPackage) {
                    emitTypeBlock(out, type, aliases.get(type.fqcn), view);
                }
                if (!pkg.isBlank()) {
                    out.println("}");
                    out.println();
                }
            }

            emitPackageOrderingHints(out, includedTypes.values(), aliases);

            for (UmlRelation relation : relations) {
                emitRelation(out, relation, aliases, view);
            }
            out.println("@enduml");
        }
    }

    private static void emitTypeBlock(PrintWriter out, UmlType type, String alias, DiagramView view) {
        String keyword = plantUmlKeyword(type.kind);
        String stereotype = formatPlantUmlStereotypes(type.stereotypes);
        String color = colorForType(type);
        out.printf("%s \"%s\" as %s%s %s {%n", keyword, escapePlantUml(type.displayName()), alias, stereotype, color);
        if (view.showFields) {
            for (UmlField field : type.fields.stream().sorted(Comparator.comparing(f -> f.name)).toList()) {
                out.printf("  %s %s : %s%n",
                        field.visibility,
                        escapePlantUml(field.name),
                        sanitizeMemberType(field.typeRaw));
            }
        }
        if (view.showFields && view.showMethods && !type.fields.isEmpty() && !type.methods.isEmpty()) {
            out.println("  --");
        }
        if (view.showMethods) {
            for (UmlMethod method : type.methods.stream().sorted(Comparator.comparing(m -> m.name)).toList()) {
                String params = method.parameterTypes.stream()
                        .map(typeUse -> sanitizeMemberType(typeUse.raw))
                        .collect(Collectors.joining(", "));
                String ret = method.isConstructor ? "" : " : " + sanitizeMemberType(method.returnTypeRaw);
                out.printf("  %s %s(%s)%s%n",
                        method.visibility,
                        escapePlantUml(method.name),
                        params,
                        ret);
            }
        }
        out.println("}");
        out.println();
    }

    private static void emitCommonSkinParams(PrintWriter out) {
        out.println("skinparam linetype ortho");
        out.println("skinparam shadowing false");
        out.println("skinparam classAttributeIconSize 0");
        out.println("skinparam packageStyle rectangle");
        out.println("skinparam ArrowFontSize 11");
        out.println("skinparam packageTitleAlignment left");
    }

    private static void emitLegend(PrintWriter out) {
        out.println("legend right");
        out.println("  <back:" + LAYER_COLORS.get("ui") + ">UI</back>");
        out.println("  <back:" + LAYER_COLORS.get("service") + ">Service</back>");
        out.println("  <back:" + LAYER_COLORS.get("repository") + ">Repository/Data</back>");
        out.println("  <back:" + LAYER_COLORS.get("auth") + ">Auth/ViewModel</back>");
        out.println("  <back:" + LAYER_COLORS.get("model") + ">Model</back>");
        out.println("  <back:" + LAYER_COLORS.get("utility") + ">Utility/Other</back>");
        out.println("endlegend");
        out.println();
    }

    private static void emitPackageOrderingHints(PrintWriter out, Collection<UmlType> types, Map<String, String> aliases) {
        Map<String, String> anchors = new LinkedHashMap<>();
        List<UmlType> sorted = new ArrayList<>(types);
        sorted.sort(Comparator.comparing((UmlType type) -> type.packageName, AndroidUmlExtractor::comparePackages)
                .thenComparing(UmlType::displayName));
        for (UmlType type : sorted) {
            anchors.putIfAbsent(type.packageName, aliases.get(type.fqcn));
        }
        List<String> orderedAnchors = new ArrayList<>(anchors.values());
        for (int i = 0; i + 1 < orderedAnchors.size(); i++) {
            out.printf("%s -[hidden]-> %s%n", orderedAnchors.get(i), orderedAnchors.get(i + 1));
        }
        if (!orderedAnchors.isEmpty()) {
            out.println();
        }
    }

    private static Set<String> selectTypesForView(UmlProject project, DiagramView view) {
        if (view.includeAllTypes) {
            return new LinkedHashSet<>(project.types.keySet());
        }
        LinkedHashSet<String> base = project.types.values().stream()
                .filter(view.baseTypeFilter)
                .map(type -> type.fqcn)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!view.includeOneHopNeighbors) {
            return base;
        }
        LinkedHashSet<String> expanded = new LinkedHashSet<>(base);
        for (UmlRelation relation : project.relations.values()) {
            if (!view.allowedKinds.contains(relation.kind)) {
                continue;
            }
            if (base.contains(relation.from) && project.types.containsKey(relation.to)) {
                expanded.add(relation.to);
            }
            if (base.contains(relation.to) && project.types.containsKey(relation.from)) {
                expanded.add(relation.from);
            }
        }
        return expanded;
    }

    private static List<UmlRelation> selectRelationsForView(UmlProject project, Set<String> includedTypeIds, DiagramView view) {
        List<UmlRelation> filtered = project.sortedRelations().stream()
                .filter(relation -> includedTypeIds.contains(relation.from) && includedTypeIds.contains(relation.to))
                .filter(relation -> view.allowedKinds.contains(relation.kind))
                .toList();
        if (!view.compactRelations) {
            return filtered;
        }

        Map<String, List<UmlRelation>> byPair = new LinkedHashMap<>();
        for (UmlRelation relation : filtered) {
            String key = relation.from + "|" + relation.to;
            byPair.computeIfAbsent(key, ignored -> new ArrayList<>()).add(relation);
        }

        List<UmlRelation> result = new ArrayList<>();
        for (List<UmlRelation> group : byPair.values()) {
            List<UmlRelation> inheritance = group.stream()
                    .filter(relation -> relation.kind == RelationKind.GENERALIZATION || relation.kind == RelationKind.REALIZATION)
                    .toList();
            result.addAll(inheritance);

            List<UmlRelation> remaining = group.stream()
                    .filter(relation -> relation.kind != RelationKind.GENERALIZATION && relation.kind != RelationKind.REALIZATION)
                    .toList();
            if (remaining.isEmpty()) {
                continue;
            }

            RelationKind strongest = strongestKind(remaining, view);
            if (strongest == null) {
                continue;
            }
            List<UmlRelation> chosen = remaining.stream()
                    .filter(relation -> relation.kind == strongest)
                    .toList();
            result.add(mergeRelations(chosen, view));
        }

        return result.stream()
                .sorted(Comparator.comparing((UmlRelation relation) -> relation.kind.name())
                        .thenComparing(relation -> relation.from)
                        .thenComparing(relation -> relation.to)
                        .thenComparing(relation -> relation.label))
                .toList();
    }

    private static RelationKind strongestKind(List<UmlRelation> relations, DiagramView view) {
        List<RelationKind> ranking = List.of(
                RelationKind.COMPOSITION,
                RelationKind.ASSOCIATION,
                RelationKind.NAVIGATION,
                RelationKind.DEPENDENCY
        );
        for (RelationKind kind : ranking) {
            if (!view.allowedKinds.contains(kind)) {
                continue;
            }
            if (relations.stream().anyMatch(relation -> relation.kind == kind)) {
                return kind;
            }
        }
        return null;
    }

    private static UmlRelation mergeRelations(List<UmlRelation> relations, DiagramView view) {
        UmlRelation seed = relations.get(0);
        String fromMultiplicity = view.showMultiplicity ? mergeMultiplicity(relations.stream().map(relation -> relation.fromMultiplicity).toList()) : "";
        String toMultiplicity = view.showMultiplicity ? mergeMultiplicity(relations.stream().map(relation -> relation.toMultiplicity).toList()) : "";
        String label = view.showEdgeLabels ? summarizeRelationLabel(relations, view) : "";
        String confidence = mergeConfidence(relations.stream().map(relation -> relation.confidence).toList());
        String evidence = relations.stream().map(relation -> relation.evidence).distinct().collect(Collectors.joining(", "));
        return new UmlRelation(seed.kind, seed.from, seed.to, fromMultiplicity, toMultiplicity, label, evidence, confidence);
    }

    private static String summarizeRelationLabel(List<UmlRelation> relations, DiagramView view) {
        RelationKind kind = relations.get(0).kind;
        if (kind == RelationKind.DEPENDENCY) {
            return "uses";
        }
        if (kind == RelationKind.NAVIGATION) {
            return view.compactLabels ? "navigates" : summarizeLabels(relations.stream().map(relation -> relation.label).toList(), 2, 24);
        }
        List<String> labels = relations.stream()
                .map(relation -> relation.label)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (labels.isEmpty()) {
            return "";
        }
        if (view.compactLabels) {
            if (labels.size() == 1) {
                return labels.get(0);
            }
            return labels.size() + " members";
        }
        return summarizeLabels(labels, 3, 36);
    }

    private static String summarizeLabels(List<String> labels, int maxCount, int maxLength) {
        List<String> cleaned = labels.stream()
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (cleaned.isEmpty()) {
            return "";
        }
        if (cleaned.size() == 1) {
            return cleaned.get(0);
        }
        String joined = String.join(", ", cleaned.subList(0, Math.min(cleaned.size(), maxCount)));
        if (cleaned.size() <= maxCount && joined.length() <= maxLength) {
            return joined;
        }
        return cleaned.size() + " members";
    }

    private static String mergeMultiplicity(List<String> multiplicities) {
        if (multiplicities.stream().anyMatch("0..*"::equals)) {
            return "0..*";
        }
        if (multiplicities.stream().anyMatch("1"::equals)) {
            return "1";
        }
        if (multiplicities.stream().anyMatch("0..1"::equals)) {
            return "0..1";
        }
        return "";
    }

    private static String mergeConfidence(List<String> confidences) {
        if (confidences.stream().anyMatch("high"::equals)) {
            return "high";
        }
        if (confidences.stream().anyMatch("medium"::equals)) {
            return "medium";
        }
        return "low";
    }

    private static void emitRelation(PrintWriter out, UmlRelation relation, Map<String, String> aliases, DiagramView view) {
        String from = aliases.get(relation.from);
        String to = aliases.get(relation.to);
        if (from == null || to == null) {
            return;
        }
        switch (relation.kind) {
            case GENERALIZATION -> out.printf("%s <|-- %s%n", to, from);
            case REALIZATION -> out.printf("%s <|.. %s%n", to, from);
            case ASSOCIATION -> emitLabeledEdge(out, from, to, "-->", relation, view);
            case COMPOSITION -> emitLabeledEdge(out, from, to, "*--", relation, view);
            case DEPENDENCY -> emitLabeledEdge(out, from, to, "..>", relation, view);
            case NAVIGATION -> emitLabeledEdge(out, from, to, "..>", relation, view);
        }
    }

    private static void emitLabeledEdge(PrintWriter out, String from, String to, String arrow, UmlRelation relation, DiagramView view) {
        String leftMult = relation.fromMultiplicity == null ? "" : relation.fromMultiplicity.trim();
        String rightMult = relation.toMultiplicity == null ? "" : relation.toMultiplicity.trim();
        boolean showMult = view.showMultiplicity && (!leftMult.isBlank() || !rightMult.isBlank())
                && (relation.kind == RelationKind.ASSOCIATION || relation.kind == RelationKind.COMPOSITION);

        StringBuilder line = new StringBuilder();
        line.append(from).append(' ');
        if (showMult) {
            line.append('"').append(escapePlantUml(leftMult.isBlank() ? "-" : leftMult)).append('"').append(' ');
        }
        line.append(arrow).append(' ');
        if (showMult) {
            line.append('"').append(escapePlantUml(rightMult.isBlank() ? "-" : rightMult)).append('"').append(' ');
        }
        line.append(to);
        if (view.showEdgeLabels && relation.label != null && !relation.label.isBlank()) {
            line.append(" : ").append(escapePlantUml(relation.label));
        }
        out.println(line);
    }

    private static void writePackageDependencyPlantUml(Path outFile, UmlProject project) throws IOException {
        Map<String, String> aliases = new LinkedHashMap<>();
        List<String> packages = project.types.values().stream()
                .map(type -> type.packageName)
                .filter(pkg -> pkg != null && !pkg.isBlank())
                .distinct()
                .sorted(AndroidUmlExtractor::comparePackages)
                .toList();
        int i = 1;
        for (String pkg : packages) {
            aliases.put(pkg, "P" + i++);
        }

        Map<String, List<UmlRelation>> grouped = new LinkedHashMap<>();
        for (UmlRelation relation : project.relations.values()) {
            UmlType fromType = project.types.get(relation.from);
            UmlType toType = project.types.get(relation.to);
            if (fromType == null || toType == null) {
                continue;
            }
            String fromPkg = fromType.packageName;
            String toPkg = toType.packageName;
            if (fromPkg == null || toPkg == null || fromPkg.isBlank() || toPkg.isBlank() || fromPkg.equals(toPkg)) {
                continue;
            }
            grouped.computeIfAbsent(fromPkg + "|" + toPkg, ignored -> new ArrayList<>()).add(relation);
        }

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(outFile, StandardCharsets.UTF_8))) {
            out.println("@startuml");
            out.println("left to right direction");
            emitCommonSkinParams(out);
            out.println("title Package dependency overview");
            out.println();

            if (packages.isEmpty()) {
                out.println("note \"No project-owned packages found.\" as N1");
                out.println("@enduml");
                return;
            }

            for (String pkg : packages) {
                out.printf("rectangle \"%s\" as %s%n", escapePlantUml(pkg), aliases.get(pkg));
            }
            out.println();

            for (int idx = 0; idx + 1 < packages.size(); idx++) {
                out.printf("%s -[hidden]-> %s%n", aliases.get(packages.get(idx)), aliases.get(packages.get(idx + 1)));
            }
            if (packages.size() > 1) {
                out.println();
            }

            List<PackageRelation> packageRelations = new ArrayList<>();
            for (Map.Entry<String, List<UmlRelation>> entry : grouped.entrySet()) {
                List<UmlRelation> relations = entry.getValue();
                RelationKind strongest = strongestPackageKind(relations);
                String[] parts = entry.getKey().split("\\|", 2);
                packageRelations.add(new PackageRelation(parts[0], parts[1], strongest, summarizePackageLabel(relations)));
            }
            packageRelations.sort(Comparator.comparing((PackageRelation relation) -> relation.from, AndroidUmlExtractor::comparePackages)
                    .thenComparing(relation -> relation.to, AndroidUmlExtractor::comparePackages)
                    .thenComparing(relation -> relation.kind.name()));

            for (PackageRelation relation : packageRelations) {
                String from = aliases.get(relation.from);
                String to = aliases.get(relation.to);
                String arrow = (relation.kind == RelationKind.DEPENDENCY || relation.kind == RelationKind.NAVIGATION) ? "..>" : "-->";
                out.printf("%s %s %s : %s%n", from, arrow, to, escapePlantUml(relation.label));
            }
            out.println("@enduml");
        }
    }

    private static RelationKind strongestPackageKind(List<UmlRelation> relations) {
        if (relations.stream().anyMatch(relation -> relation.kind == RelationKind.COMPOSITION)) return RelationKind.COMPOSITION;
        if (relations.stream().anyMatch(relation -> relation.kind == RelationKind.ASSOCIATION)) return RelationKind.ASSOCIATION;
        if (relations.stream().anyMatch(relation -> relation.kind == RelationKind.NAVIGATION)) return RelationKind.NAVIGATION;
        if (relations.stream().anyMatch(relation -> relation.kind == RelationKind.REALIZATION)) return RelationKind.REALIZATION;
        if (relations.stream().anyMatch(relation -> relation.kind == RelationKind.GENERALIZATION)) return RelationKind.GENERALIZATION;
        return RelationKind.DEPENDENCY;
    }

    private static String summarizePackageLabel(List<UmlRelation> relations) {
        long structural = relations.stream().filter(relation -> relation.kind == RelationKind.ASSOCIATION || relation.kind == RelationKind.COMPOSITION).count();
        long nav = relations.stream().filter(relation -> relation.kind == RelationKind.NAVIGATION).count();
        long deps = relations.stream().filter(relation -> relation.kind == RelationKind.DEPENDENCY).count();
        List<String> parts = new ArrayList<>();
        if (structural > 0) parts.add(structural + " structural");
        if (nav > 0) parts.add(nav + " nav");
        if (deps > 0) parts.add(deps + " uses");
        return parts.isEmpty() ? relations.size() + " refs" : String.join(", ", parts);
    }

    private static String plantUmlKeyword(TypeKind kind) {
        return switch (kind) {
            case CLASS, RECORD -> "class";
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case ANNOTATION -> "annotation";
        };
    }

    private static String sanitizeMemberType(String raw) {
        return raw == null ? "" : escapePlantUml(raw.replace('\n', ' ').replace("\r", " ").trim());
    }

    private static boolean isExternalType(String fqcn) {
        if (fqcn == null) {
            return false;
        }
        for (String prefix : DEFAULT_EXTERNAL_PREFIXES) {
            if (fqcn.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String fqcn) {
        if (fqcn == null) {
            return "";
        }
        String normalized = fqcn.replace('$', '.');
        int last = normalized.lastIndexOf('.');
        return last >= 0 ? normalized.substring(last + 1) : normalized;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String formatStereotypes(Set<String> stereotypes) {
        if (stereotypes.isEmpty()) {
            return "";
        }
        return " <<" + String.join(">> <<", stereotypes) + ">>";
    }

    private static String formatPlantUmlStereotypes(Set<String> stereotypes) {
        if (stereotypes.isEmpty()) {
            return "";
        }
        return " <<" + String.join(">> <<", stereotypes) + ">>";
    }

    private static String escapePlantUml(String value) {
        return value == null ? "" : value.replace("\"", "'").replace("\n", " ").replace("\r", " ").trim();
    }

    private static String colorForType(UmlType type) {
        return LAYER_COLORS.getOrDefault(classifyLayer(type), LAYER_COLORS.get("other"));
    }

    private static String classifyLayer(UmlType type) {
        Set<String> stereotypes = type.stereotypes;
        String pkg = type.packageName == null ? "" : type.packageName.toLowerCase(Locale.ROOT);
        String name = type.simpleName.toLowerCase(Locale.ROOT);

        if (stereotypes.contains("activity") || stereotypes.contains("fragment") || stereotypes.contains("dialog")
                || pkg.contains(".ui") || name.endsWith("activity") || name.endsWith("fragment")
                || name.endsWith("adapter") || name.endsWith("viewholder") || name.endsWith("view")) {
            return "ui";
        }
        if (pkg.contains(".viewmodel") || name.endsWith("viewmodel")) {
            return "viewmodel";
        }
        if (stereotypes.contains("service") || pkg.contains(".service") || name.endsWith("service")) {
            return "service";
        }
        if (pkg.contains(".repository") || pkg.contains(".data") || name.endsWith("repository") || name.endsWith("dao")) {
            return "repository";
        }
        if (pkg.contains(".auth") || name.contains("auth")) {
            return "auth";
        }
        if (pkg.contains(".model") || pkg.contains(".domain") || name.endsWith("model") || name.endsWith("entity")) {
            return "model";
        }
        if (pkg.contains(".controller") || name.endsWith("controller")) {
            return "controller";
        }
        if (pkg.contains(".util") || pkg.contains(".common") || name.endsWith("util") || name.endsWith("utils") || name.endsWith("helper")) {
            return "utility";
        }
        return "other";
    }

    private static String attr(org.w3c.dom.Element element, String... names) {
        for (String name : names) {
            if (element.hasAttribute(name)) {
                return element.getAttribute(name);
            }
        }
        return null;
    }

    private static org.w3c.dom.Element firstChild(org.w3c.dom.Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : (org.w3c.dom.Element) list.item(0);
    }

    private static void walkElements(org.w3c.dom.Element element, ElementConsumer consumer) {
        consumer.accept(element);
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element childElement) {
                walkElements(childElement, consumer);
            }
        }
    }

    private static int comparePackages(String left, String right) {
        int leftWeight = packageWeight(left);
        int rightWeight = packageWeight(right);
        if (leftWeight != rightWeight) {
            return Integer.compare(leftWeight, rightWeight);
        }
        return left.compareTo(right);
    }

    private static int packageWeight(String pkg) {
        if (pkg == null) return 999;
        String lower = pkg.toLowerCase(Locale.ROOT);
        if (lower.contains(".model") || lower.contains(".domain")) return 10;
        if (lower.contains(".auth")) return 20;
        if (lower.contains(".data") || lower.contains(".repository")) return 30;
        if (lower.contains(".service")) return 40;
        if (lower.contains(".viewmodel")) return 45;
        if (lower.contains(".ui")) return 50;
        if (lower.contains(".util") || lower.contains(".common")) return 60;
        return 100;
    }

    private interface ElementConsumer {
        void accept(org.w3c.dom.Element element);
    }

    private static final class AstCollector extends TreePathScanner<Void, Void> {
        private final UmlProject project;
        private final CompilationUnitTree unit;
        private final Trees trees;
        private final String packageName;
        private final List<String> imports;
        private final Deque<String> nesting = new ArrayDeque<>();

        AstCollector(UmlProject project, CompilationUnitTree unit, Trees trees) {
            this.project = project;
            this.unit = unit;
            this.trees = trees;
            this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            this.imports = unit.getImports().stream().map(imp -> imp.getQualifiedIdentifier().toString()).toList();
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (!shouldEmitType(node)) {
                return super.visitClass(node, unused);
            }

            String simpleName = node.getSimpleName().toString();
            nesting.push(simpleName);
            String nestedPart = streamDeque(nesting).collect(Collectors.joining("."));
            String fqcn = packageName.isBlank() ? nestedPart : packageName + "." + nestedPart;

            UmlType type = project.types.computeIfAbsent(
                    fqcn,
                    ignored -> new UmlType(fqcn, simpleName, packageName, toTypeKind(node.getKind()), unit.getSourceFile().toUri().toString())
            );
            type.imports.clear();
            type.imports.addAll(imports);
            type.modifiers = visibility(node.getModifiers().getFlags().stream().map(Enum::name).toList());
            type.stereotypes.addAll(parseClassStereotypes(node));
            type.superClassRaw = node.getExtendsClause() == null ? null : node.getExtendsClause().toString();
            type.superClassHint = node.getExtendsClause() == null ? null : typeHintForTree(new TreePath(getCurrentPath(), node.getExtendsClause()), type.superClassRaw);

            type.interfacesRaw.clear();
            type.interfaceHints.clear();
            for (Tree impl : node.getImplementsClause()) {
                type.interfacesRaw.add(impl.toString());
                type.interfaceHints.add(typeHintForTree(new TreePath(getCurrentPath(), impl), impl.toString()));
            }

            type.fields.clear();
            type.methods.clear();
            type.constructorCount = 0;

            Map<String, UmlField> fieldByName = new LinkedHashMap<>();

            for (Tree member : node.getMembers()) {
                if (member instanceof VariableTree field && field.getType() != null) {
                    UmlTypeUse typeUse = typeUseForTree(new TreePath(getCurrentPath(), field.getType()), field.getType().toString());
                    Set<String> modifierNames = field.getModifiers().getFlags().stream().map(Enum::name).collect(Collectors.toSet());
                    UmlField umlField = new UmlField(
                            field.getName().toString(),
                            field.getType().toString(),
                            visibility(new ArrayList<>(modifierNames)),
                            field.getInitializer() == null ? null : field.getInitializer().toString(),
                            typeUse.targetHint,
                            typeUse.collectionLike,
                            typeUse.optionalLike,
                            typeUse.array,
                            modifierNames.contains("FINAL"),
                            hasNonNullAnnotation(field.getModifiers().getAnnotations())
                    );
                    umlField.initializedAtDeclarationWithNew = field.getInitializer() instanceof NewClassTree;
                    umlField.initializedAtDeclarationNonNull = field.getInitializer() != null && field.getInitializer().getKind() != Tree.Kind.NULL_LITERAL;
                    type.fields.add(umlField);
                    fieldByName.put(umlField.name, umlField);
                } else if (member instanceof MethodTree method) {
                    boolean isConstructor = method.getReturnType() == null;
                    if (isConstructor) {
                        type.constructorCount++;
                    }

                    List<UmlTypeUse> params = new ArrayList<>();
                    for (VariableTree param : method.getParameters()) {
                        if (param.getType() == null) {
                            continue;
                        }
                        params.add(typeUseForTree(new TreePath(getCurrentPath(), param.getType()), param.getType().toString()));
                    }

                    List<UmlTypeUse> thrown = new ArrayList<>();
                    for (ExpressionTree thrownType : method.getThrows()) {
                        thrown.add(typeUseForTree(new TreePath(getCurrentPath(), thrownType), thrownType.toString()));
                    }

                    UmlTypeUse returnTypeUse = isConstructor
                            ? UmlTypeUse.none()
                            : typeUseForTree(new TreePath(getCurrentPath(), method.getReturnType()), method.getReturnType().toString());

                    MethodUsageCollector usageCollector = new MethodUsageCollector(trees);
                    if (method.getBody() != null) {
                        usageCollector.scan(new TreePath(getCurrentPath(), method.getBody()), null);
                    }

                    String methodName = isConstructor ? simpleName : method.getName().toString();
                    UmlMethod umlMethod = new UmlMethod(
                            methodName,
                            isConstructor ? null : method.getReturnType().toString(),
                            returnTypeUse.targetHint,
                            visibility(method.getModifiers().getFlags().stream().map(Enum::name).toList()),
                            params,
                            thrown,
                            usageCollector.localTypeUses,
                            usageCollector.newTypeUses,
                            isConstructor
                    );
                    type.methods.add(umlMethod);

                    if (isConstructor && method.getBody() != null) {
                        ConstructorAssignmentCollector assignmentCollector =
                                new ConstructorAssignmentCollector(fieldByName, method.getParameters().stream().map(param -> param.getName().toString()).collect(Collectors.toSet()));
                        assignmentCollector.scan(new TreePath(getCurrentPath(), method.getBody()), null);
                        assignmentCollector.apply();
                    }
                }
            }

            super.visitClass(node, unused);
            nesting.pop();
            return null;
        }

        private boolean shouldEmitType(ClassTree node) {
            String simpleName = node.getSimpleName().toString();
            if (simpleName.isBlank()) {
                return false;
            }
            if (getCurrentPath() == null || getCurrentPath().getParentPath() == null) {
                return true;
            }
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            return switch (parent.getKind()) {
                case COMPILATION_UNIT, CLASS, INTERFACE, ENUM, ANNOTATION_TYPE, RECORD -> true;
                default -> false;
            };
        }

        private Set<String> parseClassStereotypes(ClassTree node) {
            LinkedHashSet<String> stereotypes = new LinkedHashSet<>();
            String name = node.getSimpleName().toString();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith("fragment")) stereotypes.add("fragment");
            if (lower.endsWith("activity")) stereotypes.add("activity");
            if (lower.endsWith("adapter")) stereotypes.add("adapter");
            if (lower.endsWith("viewholder")) stereotypes.add("viewholder");
            if (lower.endsWith("service")) stereotypes.add("service");
            if (lower.endsWith("repository")) stereotypes.add("repository");
            if (lower.endsWith("viewmodel")) stereotypes.add("viewmodel");
            return stereotypes;
        }

        private UmlTypeUse typeUseForTree(TreePath path, String raw) {
            if (path == null) {
                return UmlTypeUse.from(null, raw);
            }
            TypeMirror mirror = safeTypeMirror(path);
            return UmlTypeUse.from(mirror, raw);
        }

        private String typeHintForTree(TreePath path, String raw) {
            UmlTypeUse typeUse = typeUseForTree(path, raw);
            return typeUse.targetHint;
        }

        private TypeMirror safeTypeMirror(TreePath path) {
            try {
                return trees.getTypeMirror(path);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private boolean hasNonNullAnnotation(List<? extends AnnotationTree> annotations) {
            for (AnnotationTree annotation : annotations) {
                String simple = simpleName(annotation.getAnnotationType().toString());
                if (NON_NULL_ANNOTATIONS.contains(simple)) {
                    return true;
                }
            }
            return false;
        }

        private static Stream<String> streamDeque(Deque<String> deque) {
            List<String> values = new ArrayList<>(deque);
            Collections.reverse(values);
            return values.stream();
        }
    }

    private static final class MethodUsageCollector extends TreePathScanner<Void, Void> {
        private final Trees trees;
        final List<UmlTypeUse> localTypeUses = new ArrayList<>();
        final List<UmlTypeUse> newTypeUses = new ArrayList<>();

        MethodUsageCollector(Trees trees) {
            this.trees = trees;
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            if (node.getType() != null) {
                localTypeUses.add(UmlTypeUse.from(safeTypeMirror(new TreePath(getCurrentPath(), node.getType())), node.getType().toString()));
            }
            return super.visitVariable(node, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree node, Void unused) {
            if (node.getIdentifier() != null) {
                newTypeUses.add(UmlTypeUse.fromDirect(safeTypeMirror(new TreePath(getCurrentPath(), node.getIdentifier())), node.getIdentifier().toString()));
            }
            return super.visitNewClass(node, unused);
        }

        @Override
        public Void visitCatch(CatchTree node, Void unused) {
            if (node.getParameter() != null && node.getParameter().getType() != null) {
                localTypeUses.add(UmlTypeUse.from(safeTypeMirror(new TreePath(getCurrentPath(), node.getParameter().getType())), node.getParameter().getType().toString()));
            }
            return super.visitCatch(node, unused);
        }

        private TypeMirror safeTypeMirror(TreePath path) {
            try {
                return trees.getTypeMirror(path);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static final class ConstructorAssignmentCollector extends TreePathScanner<Void, Void> {
        private final Map<String, UmlField> fieldByName;
        private final Set<String> parameterNames;
        private final Set<String> newAssigned = new LinkedHashSet<>();
        private final Set<String> paramAssigned = new LinkedHashSet<>();
        private final Set<String> nullAssigned = new LinkedHashSet<>();

        ConstructorAssignmentCollector(Map<String, UmlField> fieldByName, Set<String> parameterNames) {
            this.fieldByName = fieldByName;
            this.parameterNames = parameterNames;
        }

        @Override
        public Void visitAssignment(AssignmentTree node, Void unused) {
            String fieldName = assignedFieldName(node.getVariable());
            if (fieldName != null && fieldByName.containsKey(fieldName)) {
                Tree rhs = node.getExpression();
                if (rhs instanceof NewClassTree) {
                    newAssigned.add(fieldName);
                } else if (rhs.getKind() == Tree.Kind.NULL_LITERAL) {
                    nullAssigned.add(fieldName);
                } else if (rhs instanceof IdentifierTree identifier && parameterNames.contains(identifier.getName().toString())) {
                    paramAssigned.add(fieldName);
                }
            }
            return super.visitAssignment(node, unused);
        }

        void apply() {
            for (String fieldName : newAssigned) {
                UmlField field = fieldByName.get(fieldName);
                field.constructorsAssigningNewCount++;
            }
            for (String fieldName : paramAssigned) {
                UmlField field = fieldByName.get(fieldName);
                field.constructorsAssigningParamCount++;
                field.assignedFromConstructorParam = true;
            }
            for (String fieldName : nullAssigned) {
                UmlField field = fieldByName.get(fieldName);
                field.assignedNull = true;
            }
        }

        private String assignedFieldName(ExpressionTree lhs) {
            if (lhs instanceof IdentifierTree identifier) {
                String name = identifier.getName().toString();
                return fieldByName.containsKey(name) ? name : null;
            }
            if (lhs instanceof MemberSelectTree memberSelect) {
                if (memberSelect.getExpression().toString().equals("this")) {
                    String name = memberSelect.getIdentifier().toString();
                    return fieldByName.containsKey(name) ? name : null;
                }
            }
            return null;
        }
    }

    private static TypeKind toTypeKind(Tree.Kind kind) {
        return switch (kind) {
            case INTERFACE -> TypeKind.INTERFACE;
            case ENUM -> TypeKind.ENUM;
            case ANNOTATION_TYPE -> TypeKind.ANNOTATION;
            case RECORD -> TypeKind.RECORD;
            default -> TypeKind.CLASS;
        };
    }

    private static String visibility(List<String> flags) {
        Set<String> set = new HashSet<>(flags);
        if (set.contains("PUBLIC")) return "+";
        if (set.contains("PROTECTED")) return "#";
        if (set.contains("PRIVATE")) return "-";
        return "~";
    }

    private enum TypeKind {
        CLASS, INTERFACE, ENUM, ANNOTATION, RECORD
    }

    private enum RelationKind {
        GENERALIZATION, REALIZATION, ASSOCIATION, COMPOSITION, DEPENDENCY, NAVIGATION
    }

    private static final class Config {
        final Path projectRoot;
        final String module;
        final String variant;
        final Path outDir;

        private Config(Path projectRoot, String module, String variant, Path outDir) {
            this.projectRoot = projectRoot;
            this.module = module;
            this.variant = variant;
            this.outDir = outDir;
        }

        static Config parse(String[] args) {
            Map<String, String> map = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("Unexpected argument: " + arg);
                }
                String key = arg.substring(2);
                String value = (i + 1) < args.length ? args[++i] : null;
                if (value == null || value.startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for --" + key);
                }
                map.put(key, value);
            }
            Path project = Path.of(map.getOrDefault("project", ".")).toAbsolutePath().normalize();
            String module = map.getOrDefault("module", "app");
            String variant = map.getOrDefault("variant", "debug");
            Path out = Path.of(map.getOrDefault("out", "uml-out")).toAbsolutePath().normalize();
            return new Config(project, module, variant, out);
        }
    }

    private static final class ProjectFiles {
        final Path moduleRoot;
        final List<Path> javaFiles;
        final Path manifestPath;
        final List<Path> navigationXmls;
        final List<Path> javaRoots;

        private ProjectFiles(Path moduleRoot, List<Path> javaFiles, Path manifestPath, List<Path> navigationXmls, List<Path> javaRoots) {
            this.moduleRoot = moduleRoot;
            this.javaFiles = javaFiles;
            this.manifestPath = manifestPath;
            this.navigationXmls = navigationXmls;
            this.javaRoots = javaRoots;
        }
    }

    private static final class UmlProject {
        final Path projectRoot;
        final String module;
        final String variant;
        final Map<String, UmlType> types = new LinkedHashMap<>();
        final Map<String, UmlRelation> relations = new LinkedHashMap<>();
        final Set<String> warnings = new LinkedHashSet<>();

        UmlProject(Path projectRoot, String module, String variant) {
            this.projectRoot = projectRoot;
            this.module = module;
            this.variant = variant;
        }

        void addRelation(UmlRelation relation) {
            String key = relation.kind + "|" + relation.from + "|" + relation.to + "|" + relation.label;
            relations.putIfAbsent(key, relation);
        }

        List<UmlType> sortedTypes() {
            return types.values().stream()
                    .sorted(Comparator.comparing((UmlType type) -> type.packageName, AndroidUmlExtractor::comparePackages)
                            .thenComparing(UmlType::displayName))
                    .toList();
        }

        List<UmlRelation> sortedRelations() {
            return relations.values().stream()
                    .sorted(Comparator.comparing((UmlRelation relation) -> relation.kind.name())
                            .thenComparing(relation -> relation.from)
                            .thenComparing(relation -> relation.to)
                            .thenComparing(relation -> relation.label))
                    .toList();
        }
    }

    private static final class UmlType {
        final String fqcn;
        final String simpleName;
        final String packageName;
        final TypeKind kind;
        final String sourceFile;
        String modifiers = "~";
        String superClassRaw;
        String superClassHint;
        int constructorCount;
        final List<String> interfacesRaw = new ArrayList<>();
        final List<String> interfaceHints = new ArrayList<>();
        final List<String> imports = new ArrayList<>();
        final List<UmlField> fields = new ArrayList<>();
        final List<UmlMethod> methods = new ArrayList<>();
        final Set<String> stereotypes = new LinkedHashSet<>();

        UmlType(String fqcn, String simpleName, String packageName, TypeKind kind, String sourceFile) {
            this.fqcn = fqcn;
            this.simpleName = simpleName;
            this.packageName = packageName == null ? "" : packageName;
            this.kind = kind;
            this.sourceFile = sourceFile;
        }

        String displayName() {
            if (packageName.isBlank()) {
                return fqcn;
            }
            return fqcn.substring(packageName.length() + 1);
        }
    }

    private static final class UmlField {
        final String name;
        final String typeRaw;
        final String visibility;
        final String initializerRaw;
        final String targetHint;
        final boolean collectionLike;
        final boolean optionalLike;
        final boolean array;
        final boolean isFinal;
        final boolean nonNullAnnotated;
        boolean initializedAtDeclarationWithNew;
        boolean initializedAtDeclarationNonNull;
        int constructorsAssigningNewCount;
        int constructorsAssigningParamCount;
        boolean assignedFromConstructorParam;
        boolean assignedNull;

        UmlField(
                String name,
                String typeRaw,
                String visibility,
                String initializerRaw,
                String targetHint,
                boolean collectionLike,
                boolean optionalLike,
                boolean array,
                boolean isFinal,
                boolean nonNullAnnotated
        ) {
            this.name = name;
            this.typeRaw = typeRaw;
            this.visibility = visibility;
            this.initializerRaw = initializerRaw;
            this.targetHint = targetHint;
            this.collectionLike = collectionLike;
            this.optionalLike = optionalLike;
            this.array = array;
            this.isFinal = isFinal;
            this.nonNullAnnotated = nonNullAnnotated;
        }
    }

    private static final class UmlMethod {
        final String name;
        final String returnTypeRaw;
        final String returnTargetHint;
        final String visibility;
        final List<UmlTypeUse> parameterTypes;
        final List<UmlTypeUse> thrownTypes;
        final List<UmlTypeUse> localTypeUses;
        final List<UmlTypeUse> newTypeUses;
        final boolean isConstructor;

        UmlMethod(
                String name,
                String returnTypeRaw,
                String returnTargetHint,
                String visibility,
                List<UmlTypeUse> parameterTypes,
                List<UmlTypeUse> thrownTypes,
                List<UmlTypeUse> localTypeUses,
                List<UmlTypeUse> newTypeUses,
                boolean isConstructor
        ) {
            this.name = name;
            this.returnTypeRaw = returnTypeRaw;
            this.returnTargetHint = returnTargetHint;
            this.visibility = visibility;
            this.parameterTypes = new ArrayList<>(parameterTypes);
            this.thrownTypes = new ArrayList<>(thrownTypes);
            this.localTypeUses = dedupeTypeUses(localTypeUses);
            this.newTypeUses = dedupeTypeUses(newTypeUses);
            this.isConstructor = isConstructor;
        }

        private static List<UmlTypeUse> dedupeTypeUses(List<UmlTypeUse> input) {
            LinkedHashMap<String, UmlTypeUse> deduped = new LinkedHashMap<>();
            for (UmlTypeUse use : input) {
                if (use.raw == null || use.raw.isBlank()) {
                    continue;
                }
                String key = use.raw + "|" + blankToEmpty(use.targetHint);
                deduped.putIfAbsent(key, use);
            }
            return new ArrayList<>(deduped.values());
        }

        private static String blankToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private static final class UmlRelation {
        final RelationKind kind;
        final String from;
        final String to;
        final String fromMultiplicity;
        final String toMultiplicity;
        final String label;
        final String evidence;
        final String confidence;

        UmlRelation(RelationKind kind, String from, String to, String fromMultiplicity, String toMultiplicity, String label, String evidence, String confidence) {
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.fromMultiplicity = fromMultiplicity;
            this.toMultiplicity = toMultiplicity;
            this.label = label == null ? "" : label;
            this.evidence = evidence;
            this.confidence = confidence;
        }
    }

    private static final class DiagramView {
        final String title;
        final String direction;
        final boolean includeAllTypes;
        final Predicate<UmlType> baseTypeFilter;
        final boolean includeOneHopNeighbors;
        final boolean showFields;
        final boolean showMethods;
        final boolean showEdgeLabels;
        final boolean showMultiplicity;
        final boolean compactRelations;
        final boolean compactLabels;
        final EnumSet<RelationKind> allowedKinds;

        private DiagramView(
                String title,
                String direction,
                boolean includeAllTypes,
                Predicate<UmlType> baseTypeFilter,
                boolean includeOneHopNeighbors,
                boolean showFields,
                boolean showMethods,
                boolean showEdgeLabels,
                boolean showMultiplicity,
                boolean compactRelations,
                boolean compactLabels,
                EnumSet<RelationKind> allowedKinds
        ) {
            this.title = title;
            this.direction = direction;
            this.includeAllTypes = includeAllTypes;
            this.baseTypeFilter = baseTypeFilter;
            this.includeOneHopNeighbors = includeOneHopNeighbors;
            this.showFields = showFields;
            this.showMethods = showMethods;
            this.showEdgeLabels = showEdgeLabels;
            this.showMultiplicity = showMultiplicity;
            this.compactRelations = compactRelations;
            this.compactLabels = compactLabels;
            this.allowedKinds = allowedKinds.clone();
        }

        static DiagramView overview() {
            return new DiagramView(
                    "Architecture overview",
                    "left to right direction",
                    true,
                    type -> true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    EnumSet.of(RelationKind.GENERALIZATION, RelationKind.REALIZATION, RelationKind.ASSOCIATION, RelationKind.COMPOSITION)
            );
        }

        static DiagramView domainModel() {
            return new DiagramView(
                    "Domain model",
                    "left to right direction",
                    false,
                    type -> classifyLayer(type).equals("model"),
                    false,
                    true,
                    false,
                    true,
                    true,
                    true,
                    false,
                    EnumSet.of(RelationKind.GENERALIZATION, RelationKind.REALIZATION, RelationKind.ASSOCIATION, RelationKind.COMPOSITION)
            );
        }

        static DiagramView serviceData() {
            return new DiagramView(
                    "Service and data structure",
                    "left to right direction",
                    false,
                    type -> {
                        String layer = classifyLayer(type);
                        return layer.equals("service") || layer.equals("repository") || layer.equals("auth") || layer.equals("viewmodel");
                    },
                    true,
                    true,
                    false,
                    true,
                    false,
                    true,
                    true,
                    EnumSet.of(RelationKind.GENERALIZATION, RelationKind.REALIZATION, RelationKind.ASSOCIATION, RelationKind.COMPOSITION)
            );
        }

        static DiagramView uiStructure() {
            return new DiagramView(
                    "UI structure",
                    "left to right direction",
                    false,
                    type -> classifyLayer(type).equals("ui"),
                    true,
                    false,
                    false,
                    true,
                    false,
                    true,
                    true,
                    EnumSet.of(RelationKind.GENERALIZATION, RelationKind.REALIZATION, RelationKind.ASSOCIATION, RelationKind.COMPOSITION, RelationKind.NAVIGATION)
            );
        }

        static DiagramView screenNavigation() {
            return new DiagramView(
                    "Screen navigation",
                    "left to right direction",
                    false,
                    type -> classifyLayer(type).equals("ui"),
                    false,
                    false,
                    false,
                    true,
                    false,
                    true,
                    true,
                    EnumSet.of(RelationKind.NAVIGATION)
            );
        }

        static DiagramView fullReference() {
            return new DiagramView(
                    "Detailed class reference",
                    "left to right direction",
                    true,
                    type -> true,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    EnumSet.allOf(RelationKind.class)
            );
        }
    }

    private static final class PackageRelation {
        final String from;
        final String to;
        final RelationKind kind;
        final String label;

        private PackageRelation(String from, String to, RelationKind kind, String label) {
            this.from = from;
            this.to = to;
            this.kind = kind;
            this.label = label;
        }
    }

    private static final class TypeRef {
        final String outerSimple;
        final String target;
        final boolean array;

        private TypeRef(String outerSimple, String target, boolean array) {
            this.outerSimple = outerSimple;
            this.target = target;
            this.array = array;
        }

        static TypeRef parse(String rawType) {
            String cleaned = rawType == null ? "" : rawType.replace("? extends ", "").replace("? super ", "").trim();
            boolean array = cleaned.endsWith("[]");
            if (array) {
                cleaned = cleaned.substring(0, cleaned.length() - 2).trim();
            }
            String outer = cleaned;
            String genericInner = null;
            int lt = cleaned.indexOf('<');
            int gt = cleaned.lastIndexOf('>');
            if (lt > 0 && gt > lt) {
                outer = cleaned.substring(0, lt).trim();
                String inside = cleaned.substring(lt + 1, gt).trim();
                List<String> genericParts = splitTopLevelCommas(inside);
                String outerSimple = simpleName(outer);
                if (outerSimple.equals("Map") || outerSimple.endsWith("Map")) {
                    genericInner = genericParts.size() >= 2 ? genericParts.get(1).trim() : null;
                } else {
                    genericInner = genericParts.isEmpty() ? null : genericParts.get(0).trim();
                }
            }
            String outerSimple = simpleName(outer);
            String target = genericInner != null ? stripAnnotationsAndBounds(genericInner) : stripAnnotationsAndBounds(outer);
            target = target.replace("[]", "").trim();
            return new TypeRef(outerSimple, target, array);
        }

        private static String stripAnnotationsAndBounds(String raw) {
            Matcher matcher = IDENTIFIER.matcher(raw);
            String last = null;
            while (matcher.find()) {
                String token = matcher.group();
                if (token.equals("extends") || token.equals("super")) {
                    continue;
                }
                last = token;
            }
            return last == null ? raw : last;
        }

        private static List<String> splitTopLevelCommas(String raw) {
            List<String> parts = new ArrayList<>();
            int depth = 0;
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '<') depth++;
                if (c == '>') depth--;
                if (c == ',' && depth == 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
            }
            return parts;
        }
    }

    private static final class UmlTypeUse {
        final String raw;
        final String targetHint;
        final boolean collectionLike;
        final boolean optionalLike;
        final boolean array;

        private UmlTypeUse(String raw, String targetHint, boolean collectionLike, boolean optionalLike, boolean array) {
            this.raw = raw;
            this.targetHint = targetHint;
            this.collectionLike = collectionLike;
            this.optionalLike = optionalLike;
            this.array = array;
        }

        static UmlTypeUse none() {
            return new UmlTypeUse("", null, false, false, false);
        }

        static UmlTypeUse fromDirect(TypeMirror mirror, String raw) {
            TypeRef parsed = TypeRef.parse(raw);
            boolean array = parsed.array || (mirror != null && mirror.getKind() == javax.lang.model.type.TypeKind.ARRAY);
            TypeMirror effectiveMirror = mirror;
            if (mirror != null && mirror.getKind() == javax.lang.model.type.TypeKind.ARRAY) {
                effectiveMirror = ((ArrayType) mirror).getComponentType();
            }
            String outerSimple = parsed.outerSimple;
            String outerFromMirror = erasureSimpleName(effectiveMirror);
            if (outerFromMirror != null && !outerFromMirror.isBlank()) {
                outerSimple = outerFromMirror;
            }
            boolean collectionLike = COLLECTION_LIKE.contains(outerSimple);
            boolean optionalLike = "Optional".equals(outerSimple);
            String targetHint = typeMirrorToQualifiedName(effectiveMirror);
            if (targetHint == null && parsed.target != null && parsed.target.contains(".")) {
                targetHint = parsed.target.replace('$', '.');
            }
            return new UmlTypeUse(raw, targetHint, collectionLike, optionalLike, array);
        }

        static UmlTypeUse from(TypeMirror mirror, String raw) {
            TypeRef parsed = TypeRef.parse(raw);
            boolean array = parsed.array || (mirror != null && mirror.getKind() == javax.lang.model.type.TypeKind.ARRAY);

            TypeMirror effectiveMirror = mirror;
            String outerSimple = parsed.outerSimple;
            if (mirror != null && mirror.getKind() == javax.lang.model.type.TypeKind.ARRAY) {
                effectiveMirror = ((ArrayType) mirror).getComponentType();
            }

            String outerFromMirror = erasureSimpleName(effectiveMirror);
            if (outerFromMirror != null && !outerFromMirror.isBlank()) {
                outerSimple = outerFromMirror;
            }

            boolean collectionLike = COLLECTION_LIKE.contains(outerSimple);
            boolean optionalLike = "Optional".equals(outerSimple);

            String targetHint = null;
            if (collectionLike || optionalLike) {
                targetHint = typeMirrorToRelevantArgument(effectiveMirror, collectionLike, optionalLike);
            }
            if (targetHint == null) {
                targetHint = typeMirrorToQualifiedName(effectiveMirror);
            }
            if (targetHint == null && parsed.target != null && parsed.target.contains(".")) {
                targetHint = parsed.target.replace('$', '.');
            }

            return new UmlTypeUse(raw, targetHint, collectionLike, optionalLike, array);
        }

        private static String erasureSimpleName(TypeMirror mirror) {
            if (mirror == null) {
                return null;
            }
            if (mirror.getKind() == javax.lang.model.type.TypeKind.DECLARED || mirror.getKind() == javax.lang.model.type.TypeKind.ERROR) {
                DeclaredType declared = (DeclaredType) mirror;
                if (declared.asElement() instanceof TypeElement element) {
                    return element.getSimpleName().toString();
                }
            }
            return null;
        }

        private static String typeMirrorToRelevantArgument(TypeMirror mirror, boolean collectionLike, boolean optionalLike) {
            if (mirror == null) {
                return null;
            }
            if (!(mirror.getKind() == javax.lang.model.type.TypeKind.DECLARED || mirror.getKind() == javax.lang.model.type.TypeKind.ERROR)) {
                return null;
            }
            DeclaredType declared = (DeclaredType) mirror;
            List<? extends TypeMirror> args = declared.getTypeArguments();
            if (args.isEmpty()) {
                return null;
            }
            TypeMirror target;
            String outerSimple = erasureSimpleName(mirror);
            if ((outerSimple != null && (outerSimple.equals("Map") || outerSimple.endsWith("Map"))) && args.size() >= 2) {
                target = args.get(1);
            } else {
                target = args.get(0);
            }
            return typeMirrorToQualifiedName(unwrapBounds(target));
        }

        private static TypeMirror unwrapBounds(TypeMirror mirror) {
            if (mirror == null) {
                return null;
            }
            return switch (mirror.getKind()) {
                case WILDCARD -> {
                    WildcardType wildcard = (WildcardType) mirror;
                    if (wildcard.getExtendsBound() != null) yield unwrapBounds(wildcard.getExtendsBound());
                    if (wildcard.getSuperBound() != null) yield unwrapBounds(wildcard.getSuperBound());
                    yield null;
                }
                case TYPEVAR -> unwrapBounds(((TypeVariable) mirror).getUpperBound());
                default -> mirror;
            };
        }

        private static String typeMirrorToQualifiedName(TypeMirror mirror) {
            if (mirror == null) {
                return null;
            }
            TypeMirror unwrapped = unwrapBounds(mirror);
            if (unwrapped == null) {
                return null;
            }
            return switch (unwrapped.getKind()) {
                case ARRAY -> typeMirrorToQualifiedName(((ArrayType) unwrapped).getComponentType());
                case DECLARED, ERROR -> {
                    DeclaredType declared = (DeclaredType) unwrapped;
                    if (declared.asElement() instanceof TypeElement element) {
                        yield element.getQualifiedName().toString().replace('$', '.');
                    }
                    yield null;
                }
                default -> null;
            };
        }
    }
}
