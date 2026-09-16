---
name: openapi-generate-server-jar
description: Compile a hand-derived OpenAPI 3.0 spec (from the spring-openapi skill) into a Spring server-interface Java jar (API interfaces + models) using OpenAPI Generator, for wiring into this project's controllers. Use when asked to generate/regenerate/rebuild the generated-interfaces jar for a feature, or to compile a spec into Java.
---

# Compile an OpenAPI spec into a Spring server jar

Turns one `steps/2_generate_openapi_specs_output/<feature>.yaml` spec into
`steps/2_generate_openapi_specs_output/<feature>.jar` — a jar containing the
generated `<Tag>Api` interface(s) and model classes (both `.class` and
`.java`, same package layout), ready to add to `build.gradle` and `implement`
from a real controller (see the `openapi-wire-controller` skill for that
half).

There's no springdoc/openapi-generator Gradle plugin wired into this
project, so this is done with the standalone OpenAPI Generator CLI jar, not
a Gradle task.

## 1. Get the generator CLI

Pin to the version already used in this project unless told otherwise:

```
curl -L -o /tmp/openapi-generator-cli.jar \
  https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/7.9.0/openapi-generator-cli-7.9.0.jar
```

Don't commit this jar anywhere in the repo — only the generated output jar
(step 4) is checked in, to `steps/2_generate_openapi_specs_output/`.

## 2. Generate the Java sources

```
java -jar /tmp/openapi-generator-cli.jar generate \
  -i steps/2_generate_openapi_specs_output/<feature>.yaml \
  -g spring \
  -o /tmp/<feature>-gen \
  --api-package com.eazyfreight.<feature>.api \
  --model-package com.eazyfreight.<feature>.model \
  --invoker-package com.eazyfreight.<feature>.invoker \
  --additional-properties=interfaceOnly=true,useSpringBoot3=true,annotationLibrary=none,documentationProvider=none,useTags=true,dateLibrary=java8,openApiNullable=false
```

Flags, and why each one is set this way for this project:
- `interfaceOnly=true` — only `<Tag>Api` interfaces + models, no controller
  implementations or `Application` bootstrap class (the real controller
  supplies those).
- `useSpringBoot3=true` — Jakarta EE namespaces, matching this project's
  Spring Boot 3.3.2 / Java 21 stack.
- `annotationLibrary=none`, `documentationProvider=none` — no springdoc/
  swagger-annotations dependency required to compile the output.
- `useTags=true` — the generated interface is named after the spec's
  `tags:` value, not automatically `<Feature>Api`. Check
  `/tmp/<feature>-gen/src/main/java/.../api/*.java` after generation to get
  the real interface name (e.g. a spec tagged `Alerts` produces `AlertsApi`,
  not `AlertApi`) — don't assume the singular form when wiring later.
- `dateLibrary=java8`, `openApiNullable=false` — plain `java.time` types
  (`OffsetDateTime`, `LocalDate`) on model fields, no `JsonNullable` wrapper.

## 3. Resolve a compile classpath from this project's existing dependencies

Reuse the exact dependency versions already in `build.gradle` — don't add a
new springdoc/swagger dependency just to compile generated code. Resolve the
classpath without modifying `build.gradle`, via a throwaway init script:

```
cat > /tmp/print-classpath.init.gradle <<'EOF'
allprojects {
    tasks.register("printCompileClasspath") {
        doLast { println configurations.compileClasspath.asPath }
    }
}
EOF
./gradlew --init-script /tmp/print-classpath.init.gradle -q printCompileClasspath > /tmp/<feature>-classpath.txt
```

Two extra jars are needed beyond that classpath (present transitively
elsewhere in the project but not on `compileClasspath` directly) — find and
append them:
- `tomcat-embed-core` — the generator's `ApiUtil` helper references
  `jakarta.servlet.http.HttpServletResponse`.
- `jackson-databind` — needed for `@JsonDeserialize`, emitted on any
  `Set`-typed field with `uniqueItems: true` in the spec.

```
EXTRA=$(find ~/.gradle/caches/modules-2/files-2.1 \
  \( -path '*tomcat-embed-core*' -o -path '*jackson-databind*' \) \
  -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
  | sort -V | awk -F/ '!seen[$0=$(NF-2)]++' )
```

(Pick the highest resolved version if `find` turns up more than one — check
`/tmp/<feature>-classpath.txt` or `./gradlew dependencies` for which version
the project actually resolves, don't just grab the first match.)

## 4. Compile and package

```
mkdir -p /tmp/<feature>-classes
javac -cp "$(cat /tmp/<feature>-classpath.txt):$EXTRA" -d /tmp/<feature>-classes \
  $(find /tmp/<feature>-gen/src/main/java -name '*.java')

# Jar contains both compiled .class files and their .java sources, same
# package layout — no exploded directory tree kept alongside the jar.
STAGE=/tmp/<feature>-stage
rm -rf "$STAGE" && mkdir -p "$STAGE"
cp -r /tmp/<feature>-classes/. "$STAGE"/
cp -r /tmp/<feature>-gen/src/main/java/. "$STAGE"/
jar cf steps/2_generate_openapi_specs_output/<feature>.jar -C "$STAGE" .
```

Regenerating an existing feature overwrites its jar at the same path.

## 5. Wire it into the build (first time only)

If `build.gradle` doesn't already have this, add it once:

```
implementation fileTree(dir: 'steps/2_generate_openapi_specs_output', include: '*.jar')
```

Point it at the generation output directory directly — don't copy jars into
a separate `libs/` folder first, that's a redundant duplicate.

## 6. Verify

```
./gradlew clean compileJava
```

should succeed with the new/updated jar on the classpath before handing off
to `openapi-wire-controller`. If it doesn't compile, the spec (not the
generation step) is almost always the cause — re-check the YAML against the
controller/DTOs per the `spring-openapi` skill's self-verify step rather
than hand-patching the generated Java.

## Batch generation

When asked to (re)generate jars for multiple/all features, do steps 1–4 for
each spec independently and concurrently (one agent/pass per feature) — each
jar is self-contained, there's no shared state between them until step 6's
whole-project compile.
