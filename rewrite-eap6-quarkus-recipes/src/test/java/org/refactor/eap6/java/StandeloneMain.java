package org.refactor.eap6.java;

import org.openrewrite.*;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class StandeloneMain {

    static final ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

    static JavaParser jp() {
        return JavaParser.fromJavaVersion()
                .classpath(JavaParser.runtimeClasspath())
                .logCompilationWarningsAndErrors(true)
                .build();
    }

    public static void main(String[] args) {

        Environment env = Environment.builder().scanRuntimeClasspath().build();
        Recipe recipe = env.activateRecipes("org.openrewrite.java.RemoveUnusedImports");

        List<Path> sources = new ArrayList<>();
        sources.add(Path.of("E:\\GIT\\java-rewrite-recipes\\src\\test\\java\\com\\lodh\\arte\\java\\ejb\\GenerateComponentRecipeTest.java"));
        List<SourceFile> sourceFileList = jp().parse(sources, null, ctx).collect(Collectors.toList());
        InMemoryLargeSourceSet inMemoryLargeSourceSet = new InMemoryLargeSourceSet(sourceFileList);

        List<Result> allResults = recipe.run(inMemoryLargeSourceSet, ctx).getChangeset().getAllResults();
        writeResults(allResults);
    }

    private static void writeResults(List<Result> results) {
        for (Result result : results) {
            try {
                Files.writeString(requireNonNull(result.getAfter()).getSourcePath(),
                        result.getAfter().printAll());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
