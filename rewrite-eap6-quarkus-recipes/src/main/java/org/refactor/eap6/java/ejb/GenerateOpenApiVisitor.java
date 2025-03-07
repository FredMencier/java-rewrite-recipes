package org.refactor.eap6.java.ejb;

import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.Value;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;

@Value
@EqualsAndHashCode(callSuper = false)
public class GenerateOpenApiVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateOpenApiVisitor.class);

    String targetDirectory;

    @SneakyThrows
    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
        J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);
        Object message = executionContext.pollMessage(classDecl.getSimpleName());
        if (message instanceof OpenAPI) {
            OpenAPI openAPI = (OpenAPI) message;

            StringWriter stringWriter = new StringWriter();
            ParserMicroprofile.toYAML(openAPI, stringWriter);
            FileUtil fileUtil = new FileUtil();
            fileUtil.createFile(targetDirectory + "/" + classDecl.getSimpleName() + ".yaml", sanitize(stringWriter.toString()));
            LOG.info("OpenAPI file generated in: " + targetDirectory + " directory for class: " + classDecl.getSimpleName());
        }
        return classDeclaration;
    }

    /**
     * Manually sanitize the content to remove unwanted characters
     *
     * @param content
     * @return
     */
    private String sanitize(String content) {
        return content.replace("ref:", "$ref:")
                .replace("enumeration:", "enum:")
                .replace("additionalPropertiesSchema:", "additionalProperties:")
                .replace("extensions:\n", "")
                .replace("  x-", "x-");
    }

}
