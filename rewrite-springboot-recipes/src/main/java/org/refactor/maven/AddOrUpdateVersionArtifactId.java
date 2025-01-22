package org.refactor.maven;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = true)
public class AddOrUpdateVersionArtifactId extends Recipe {

    private static final Logger LOG = LoggerFactory.getLogger(AddOrUpdateVersionArtifactId.class);

    @Override
    public String getDisplayName() {
        return "Add version tag for an artifact";
    }

    @Override
    public String getDescription() {
        return "Add version tag for the specified artifactid.";
    }

    @Option(displayName = "artifactid",
            description = "artifact.",
            example = "mockwebserver")
    @NonNull
    String artifactid;

    @Option(displayName = "groupid",
            description = "groupid.",
            example = "com.squareup.okhttp3")
    @NonNull
    String groupid;

    @Option(displayName = "version",
            description = "version.",
            example = "4.12.0")
    @NonNull
    String version;

    @JsonCreator
    public AddOrUpdateVersionArtifactId(@NonNull @JsonProperty("groupid") String groupid, @NonNull @JsonProperty("artifactid") String artifactid, @NonNull @JsonProperty("version") String version) {
        this.artifactid = artifactid;
        this.groupid = groupid;
        this.version = version;
    }

    @Override
    public MavenVisitor<ExecutionContext> getVisitor() {

        return new MavenIsoVisitor<>() {

            @SuppressWarnings("OptionalGetWithoutIsPresent")
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (isDependencyTag(groupid, artifactid) && t.getContent() != null && !tag.getChild("version").isPresent()) {
                    List<Xml.Tag> contents = (List<Xml.Tag>) t.getContent().stream().filter(content -> !((Xml.Tag) content).getName().equals("version")).collect(Collectors.toList());
                    contents.add(Xml.Tag.build("<version>" + version + "</version>").withPrefixUnsafe(tag.getChild("artifactId").get().getPrefixUnsafe()));
                    return t.withContent(contents);
                }
                return t;
            }
        };
    }
}
