package org.refactor.eap6.maven;

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
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = true)
public class RemoveVersionArtifactId extends Recipe {

    private static final Logger LOG = LoggerFactory.getLogger(RemoveVersionArtifactId.class);

    @Override
    public String getDisplayName() {
        return "Remove version tag for an artifact";
    }

    @Override
    public String getDescription() {
        return "Remove version tag for the specified artifactid.";
    }

    @Option(displayName = "artifactid",
            description = "artifact.",
            example = "CdiTest")
    @NonNull
    String artifactid;

    @Option(displayName = "groupid",
            description = "groupid.",
            example = "org.refactor.eap6")
    @NonNull
    String groupid;

    @JsonCreator
    public RemoveVersionArtifactId(@NonNull @JsonProperty("groupid") String groupid, @NonNull @JsonProperty("artifactid") String artifactid) {
        this.artifactid = artifactid;
        this.groupid = groupid;
    }

    @Override
    public MavenVisitor<ExecutionContext> getVisitor() {

        return new MavenIsoVisitor<ExecutionContext>() {

            @SuppressWarnings("OptionalGetWithoutIsPresent")
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (isDependencyTag(groupid, artifactid) && t.getContent() != null && tag.getChild("version").isPresent()) {
                    List<? extends Content> version = t.getContent().stream().filter(content -> !((Xml.Tag) content).getName().equals("version")).collect(Collectors.toList());
                    return t.withContent(version);
                }
                if (t.getName().equals("plugin") && t.getContent() != null && (tag.getChild("version").isPresent() && tag.getChildValue("groupId").orElse("").equals(groupid) && tag.getChildValue("artifactId").orElse("").equals(artifactid))) {
                        List<? extends Content> version = t.getContent().stream().filter(content -> !((Xml.Tag) content).getName().equals("version")).collect(Collectors.toList());
                        return t.withContent(version);

                }
                return t;
            }
        };
    }
}
