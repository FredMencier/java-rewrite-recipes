package org.refactor.eap6.java;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Value
@EqualsAndHashCode(callSuper = false)
public class ReplaceWithInject extends Recipe {

    private static final Logger LOG = LoggerFactory.getLogger(ReplaceWithInject.class);


    @Override
    public String getDisplayName() {
        return "Refactor PropsUtil";
    }

    @Override
    public String getDescription() {
        return "Replace PropsUtil with ConfigProperty.";
    }

    @Option(displayName = "Name of bean",
        description = "class name of the bean",
        example = "MyBean")
    String beanName;


    public ReplaceWithInject(@NonNull @JsonProperty("beanName") String beanname) {
        this.beanName = beanname;
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(10);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddInjectBeanVisitor(beanName);
    }

}
