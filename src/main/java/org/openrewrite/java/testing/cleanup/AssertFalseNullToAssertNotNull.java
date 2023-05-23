/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.function.Supplier;

public class AssertFalseNullToAssertNotNull extends Recipe {
    private static final MethodMatcher ASSERT_FALSE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertFalse(..)");

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertFalse(a == null)` to `assertNotNull(a)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertNotNull(a)` is simpler and more clear.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(ASSERT_FALSE);
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            Supplier<JavaParser> javaParser = null;
            private Supplier<JavaParser> javaParser(ExecutionContext ctx) {
                if(javaParser == null) {
                    javaParser = () -> JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9")
                            .build();
                }
                return javaParser;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_FALSE.matches(mi) && isEqualBinary(mi)) {
                    StringBuilder sb = new StringBuilder();

                    J.Binary binary = (J.Binary) mi.getArguments().get(0);
                    Expression nonNullExpression = getNonNullExpression(binary);

                    if (mi.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertNotNull");
                    } else {
                        sb.append("Assertions.");
                    }
                    sb.append("assertNotNull(#{any(java.lang.Object)}");
                    Object[] args;
                    if (mi.getArguments().size() == 2) {
                        sb.append(", #{any()}");
                        args = new J[]{nonNullExpression, mi.getArguments().get(1)};
                    } else {
                        args = new J[]{nonNullExpression};
                    }
                    sb.append(")");

                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .staticImports("org.junit.jupiter.api.Assertions.assertNotNull")
                                .javaParser(javaParser(ctx)).build();
                    } else {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .imports("org.junit.jupiter.api.Assertions")
                                .javaParser(javaParser(ctx)).build();
                    }
                    return mi.withTemplate(t, mi.getCoordinates().replace(), args);
                }
                return mi;
            }


            private Expression getNonNullExpression(J.Binary binary) {

                if (binary.getRight() instanceof J.Literal){
                    boolean isNull = ((J.Literal) binary.getRight()).getValue() == null;
                    if (isNull){
                        return binary.getLeft();
                    }
                }

                return binary.getRight();
            }

            private boolean isEqualBinary(J.MethodInvocation method) {

                if (method.getArguments().isEmpty()) {
                    return false;
                }

                final Expression firstArgument = method.getArguments().get(0);
                if (!(firstArgument instanceof J.Binary)) {
                    return false;
                }

                J.Binary binary = (J.Binary) firstArgument;
                J.Binary.Type operator = binary.getOperator();
                return operator.equals(J.Binary.Type.Equal);
            }
        };
    }

}
