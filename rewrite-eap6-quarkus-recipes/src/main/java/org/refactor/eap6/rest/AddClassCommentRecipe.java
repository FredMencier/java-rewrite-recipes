package org.refactor.eap6.rest;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

public class AddClassCommentRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "devoxx name";
    }

    @Override
    public String getDescription() {
        return "devoxx name.";
    }

    private static final String DEVOXX_2024_COMMENT = "Devoxx 2024";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, executionContext);
                if (classDeclaration.getComments().stream().noneMatch(comment -> DEVOXX_2024_COMMENT.equals(((TextComment)comment).getText()))) {
                    List<Comment> comments = new ArrayList<>();
                    comments.add(new TextComment(false, DEVOXX_2024_COMMENT, "\r\n", Markers.EMPTY));
                    return classDeclaration.withComments(ListUtils.concatAll(classDeclaration.getComments(), comments));
                }
                return classDeclaration;
            }
        };
    }
}
