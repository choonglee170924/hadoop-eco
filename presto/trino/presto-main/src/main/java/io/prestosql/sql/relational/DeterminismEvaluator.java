/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.sql.relational;

import io.prestosql.metadata.FunctionRegistry;
import io.prestosql.metadata.Signature;

import static java.util.Objects.requireNonNull;

public class DeterminismEvaluator
{
    final FunctionRegistry registry;

    public DeterminismEvaluator(FunctionRegistry registry)
    {
        this.registry = requireNonNull(registry, "registry is null");
    }

    public boolean isDeterministic(RowExpression expression)
    {
        return expression.accept(new Visitor(registry), null);
    }

    private static class Visitor
            implements RowExpressionVisitor<Boolean, Void>
    {
        private final FunctionRegistry registry;

        public Visitor(FunctionRegistry registry)
        {
            this.registry = registry;
        }

        @Override
        public Boolean visitInputReference(InputReferenceExpression reference, Void context)
        {
            return true;
        }

        @Override
        public Boolean visitConstant(ConstantExpression literal, Void context)
        {
            return true;
        }

        @Override
        public Boolean visitCall(CallExpression call, Void context)
        {
            Signature signature = call.getSignature();
            if (registry.isRegistered(signature) && !registry.getScalarFunctionImplementation(signature).isDeterministic()) {
                return false;
            }

            return call.getArguments().stream()
                    .allMatch(expression -> expression.accept(this, context));
        }

        @Override
        public Boolean visitSpecialForm(SpecialForm specialForm, Void context)
        {
            return specialForm.getArguments().stream()
                    .allMatch(expression -> expression.accept(this, context));
        }

        @Override
        public Boolean visitLambda(LambdaDefinitionExpression lambda, Void context)
        {
            return lambda.getBody().accept(this, context);
        }

        @Override
        public Boolean visitVariableReference(VariableReferenceExpression reference, Void context)
        {
            return true;
        }
    }
}
