package org.refactor.eap6.maven;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.refactor.eap6.util.FileUtil;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.search.FindPlugin;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.refactor.eap6.maven.AddMavenQuarkusPluginVisitor.ARTIFACTID_QUARKUS_MAVEN_PLUGIN;
import static org.refactor.eap6.maven.AddMavenQuarkusPluginVisitor.GROUPID_IO_QUARKUS;

public class AddMavenQuarkusPlugin extends ScanningRecipe<ScannedModules> {

    @Override
    public ScannedModules getInitialValue(ExecutionContext ctx) {
        ScannedModules scanned = new ScannedModules();
        scanned.setShouldCreateConfigFiles(new AtomicBoolean(true));
        return scanned;
    }

    private static final Logger LOG = LoggerFactory.getLogger(AddMavenQuarkusPlugin.class);

    @Override
    public String getDisplayName() {
        return "Add Maven Quarkus Plugin";
    }

    @Override
    public String getDescription() {
        return "Add Maven Quarkus Plugin.";
    }

    private static final String APPLICATION = "application.yaml";

    private static final String APPLICATION_DEV = "application-dev.yaml";

    private static final String APPLICATION_TEST = "application-test.yaml";

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5L);
    }

    @Option(displayName = "Business Module",
            description = "Business Module name if needed.",
            example = "ForexServicesEJB")
    @Nullable
    String businessModuleName;

    @JsonCreator
    public AddMavenQuarkusPlugin(@JsonProperty("businessModuleName") String businessModuleName) {
        this.businessModuleName = businessModuleName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ScannedModules scanned) {
        return Preconditions.check(new FindSourceFiles("**/*.xml"), new AddMavenQuarkusPluginVisitor(scanned));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ScannedModules acc) {
        return new MavenIsoVisitor<ExecutionContext>() {

            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Document doc = super.visitDocument(document, ctx);
                Xml.Tag root = doc.getRoot();
                Set<Xml.Tag> tags = FindPlugin.find(document, GROUPID_IO_QUARKUS, ARTIFACTID_QUARKUS_MAVEN_PLUGIN);
                if (!tags.isEmpty()) {
                    acc.setShouldCreateConfigFiles(new AtomicBoolean(false));
                    return doc;
                }

                if (root.getChild("artifactId").isPresent()) {
                    Optional<String> artifactidOpt = root.getChildValue("artifactId");

                    if (businessModuleName != null) {
                        LOG.info("Use given businessModule {}", artifactidOpt.get());
                        acc.setBusinessModuleName(businessModuleName);
                    } else if (artifactidOpt.isPresent() && artifactidOpt.get().toUpperCase().contains("WEB")) {
                        LOG.info("Auto-detect businessModule {}", artifactidOpt.get());
                        acc.setBusinessModuleName(artifactidOpt.get());
                    }
                    if (artifactidOpt.isPresent() && artifactidOpt.get().toUpperCase().contains("TEST")) {
                        acc.getTestModuleNameList().add(artifactidOpt.get());
                    }
                }

                if (root.getChild("packaging").isPresent()) {
                    Optional<String> packaging = root.getChild("packaging").get().getValue();
                    if (packaging.isPresent() && packaging.get().equals("ejb")) {
                        Optional<Xml.Tag> name = root.getChild("artifactId");
                        name.ifPresent(tag -> acc.setEjbModuleName(tag.getValue().get()));
                        LOG.info("Scan ejbModule {}", acc.getEjbModuleName());
                    }
                    if (packaging.isPresent() && packaging.get().equals("ear")) {
                        Optional<Xml.Tag> name = root.getChild("artifactId");
                        name.ifPresent(tag -> acc.setEarModuleName(tag.getValue().get()));
                        LOG.info("Scan earModule {}", acc.getEarModuleName());
                    }
                    if (packaging.isPresent() && packaging.get().equals("war")) {
                        Optional<Xml.Tag> name = root.getChild("artifactId");
                        name.ifPresent(tag -> acc.setWebModuleName(tag.getValue().get()));
                        LOG.info("Scan warModule {}", acc.getWebModuleName());
                    }
                }
                return doc;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(ScannedModules acc, ExecutionContext ctx) {
        if (!"".equals(acc.getBusinessModuleName())) {
            if (acc.getShouldCreateConfigFiles().get()) {
                Collection<? extends SourceFile> collect = (Collection) YamlParser.builder().build().parse(new String[]{getContent(APPLICATION)}).map((brandNewFile) -> brandNewFile.withSourcePath(Paths.get(acc.getBusinessModuleName() + "/src/main/resources/" + APPLICATION))).collect(Collectors.toList());
                collect.addAll((Collection) YamlParser.builder().build().parse(new String[]{getContent(APPLICATION_DEV)}).map((brandNewFile) -> brandNewFile.withSourcePath(Paths.get(acc.getBusinessModuleName() + "/src/main/resources/" + APPLICATION_DEV))).collect(Collectors.toList()));
                collect.addAll((Collection) YamlParser.builder().build().parse(new String[]{getContent(APPLICATION_TEST)}).map((brandNewFile) -> brandNewFile.withSourcePath(Paths.get(acc.getBusinessModuleName() + "/src/main/resources/" + APPLICATION_TEST))).collect(Collectors.toList()));
                return collect;
            } else {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    /**
     * @param fileTemplate
     * @return
     */
    private String getContent(String fileTemplate) {
        FileUtil fileUtil = new FileUtil();
        String content = "";
        try {
            content = fileUtil.readResourceFileContent(fileTemplate);
        } catch (IOException e) {
            LOG.error("Unable to read yaml template {}", fileTemplate);
        }
        return content;
    }
}
