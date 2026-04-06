# Android UML Extractor

A small JDK-only UML extractor for pure-Java Android Studio projects.

## Goals

- Use a real Java parser: the JDK compiler AST (`JavacTask`, `com.sun.source.*`).
- Keep setup small: no Maven, no Gradle plugin, no third-party parser.
- Focus on software-engineering structure, not Android/library noise.
- Emit multiple PlantUML views that stay readable instead of one unreadable mega-graph.
- Emit a plaintext audit report so every extracted relation can be checked.

## What it extracts

### Java structure

- top-level and member classes
- interfaces, enums, annotations, records
- fields and methods
- `extends` / `implements`
- field-based structural relations
- method signature dependencies
- selected body-level dependencies from local project-owned `new` usages and local typed declarations

### Android structure

- merged manifest when present under `build/intermediates/...`
- fallback manifests under `src/<sourceSet>/AndroidManifest.xml`
- navigation graphs under `src/<sourceSet>/res/navigation/*.xml`
- Android stereotypes like `<<activity>>`, `<<fragment>>`, `<<service>>`
- navigation edges between screens

## Relation rules

- **Generalization**: from `extends`
- **Realization**: from `implements`
- **Association**: project-owned field reference
- **Composition**: conservative; private non-container field with an owned `new` instance created by the class
- **Dependency**: method return/parameter/throws plus selected body-level uses

## Multiplicity rules

- `Foo[]`, `List<Foo>`, `Set<Foo>`, `Collection<Foo>`, `Map<K, Foo>` -> `0..*`
- `Optional<Foo>` -> `0..1`
- object field -> `1` only when there is strong evidence such as final + non-null init or final + non-null constructor assignment in all constructors
- otherwise -> `0..1`

## Output files

- `overview.puml`
- `domain_model.puml`
- `service_data.puml`
- `ui_structure.puml`
- `screen_navigation.puml`
- `full_reference.puml`
- `package_dependencies.puml`
- `uml_reference.txt`

## Compile

```bash
javac --release 17 AndroidUmlExtractor.java
```

If your JDK needs it explicitly:

```bash
javac --add-modules jdk.compiler --release 17 AndroidUmlExtractor.java
```

## Run

```bash
java AndroidUmlExtractor \
  --project /path/to/project \
  --module app \
  --variant debug \
  --out /path/to/output
```

## Convenience wrappers

### Windows

```bat
run_uml_extractor.bat C:\path\to\project app debug C:\path\to\output
```

### Bash

```bash
./run_uml_extractor.sh /path/to/project app debug /path/to/output
```

## Notes

- This is **Java-only**. It does not parse Kotlin sources.
- It does **not** invoke Gradle. It searches for merged manifests if they already exist.
- Exact Android symbol resolution can be incomplete without the Android classpath, but project-owned type extraction still works well because the parser is based on the compiler AST rather than regex.
- PlantUML layout is still automatic. The extractor improves readability by splitting output into focused diagrams and compacting weaker duplicate relations.
