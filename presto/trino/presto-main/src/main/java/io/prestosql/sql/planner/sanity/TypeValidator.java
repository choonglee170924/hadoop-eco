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
package io.prestosql.sql.planner.sanity;

import com.google.common.collect.ListMultimap;
import io.prestosql.Session;
import io.prestosql.execution.warnings.WarningCollector;
import io.prestosql.metadata.Metadata;
import io.prestosql.metadata.Signature;
import io.prestosql.spi.type.Type;
import io.prestosql.spi.type.TypeManager;
import io.prestosql.spi.type.TypeSignature;
import io.prestosql.sql.planner.SimplePlanVisitor;
import io.prestosql.sql.planner.Symbol;
import io.prestosql.sql.planner.TypeAnalyzer;
import io.prestosql.sql.planner.TypeProvider;
import io.prestosql.sql.planner.plan.AggregationNode;
import io.prestosql.sql.planner.plan.AggregationNode.Aggregation;
import io.prestosql.sql.planner.plan.PlanNode;
import io.prestosql.sql.planner.plan.ProjectNode;
import io.prestosql.sql.planner.plan.UnionNode;
import io.prestosql.sql.planner.plan.WindowNode;
import io.prestosql.sql.tree.Expression;
import io.prestosql.sql.tree.FunctionCall;
import io.prestosql.sql.tree.SymbolReference;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static io.prestosql.type.UnknownType.UNKNOWN;
import static java.util.Objects.requireNonNull;

/**
 * Ensures that all the expressions and FunctionCalls matches their output symbols
 */
public final class TypeValidator
        implements PlanSanityChecker.Checker
{
    public TypeValidator() {}

    @Override
    public void validate(PlanNode plan, Session session, Metadata metadata, TypeAnalyzer typeAnalyzer, TypeProvider types, WarningCollector warningCollector)
    {
        plan.accept(new Visitor(session, metadata, typeAnalyzer, types, warningCollector), null);
    }

    private static class Visitor
            extends SimplePlanVisitor<Void>
    {
        private final Session session;
        private final Metadata metadata;
        private final TypeAnalyzer typeAnalyzer;
        private final TypeProvider types;
        private final WarningCollector warningCollector;

        public Visitor(Session session, Metadata metadata, TypeAnalyzer typeAnalyzer, TypeProvider types, WarningCollector warningCollector)
        {
            this.session = requireNonNull(session, "session is null");
            this.metadata = requireNonNull(metadata, "metadata is null");
            this.typeAnalyzer = requireNonNull(typeAnalyzer, "typeAnalyzer is null");
            this.types = requireNonNull(types, "types is null");
            this.warningCollector = requireNonNull(warningCollector, "warningCollector is null");
        }

        @Override
        public Void visitAggregation(AggregationNode node, Void context)
        {
            visitPlan(node, context);

            AggregationNode.Step step = node.getStep();

            switch (step) {
                case SINGLE:
                    checkFunctionSignature(node.getAggregations());
                    checkFunctionCall(node.getAggregations());
                    break;
                case FINAL:
                    checkFunctionSignature(node.getAggregations());
                    break;
            }

            return null;
        }

        @Override
        public Void visitWindow(WindowNode node, Void context)
        {
            visitPlan(node, context);

            checkWindowFunctions(node.getWindowFunctions());

            return null;
        }

        @Override
        public Void visitProject(ProjectNode node, Void context)
        {
            visitPlan(node, context);

            for (Map.Entry<Symbol, Expression> entry : node.getAssignments().entrySet()) {
                Type expectedType = types.get(entry.getKey());
                if (entry.getValue() instanceof SymbolReference) {
                    SymbolReference symbolReference = (SymbolReference) entry.getValue();
                    verifyTypeSignature(entry.getKey(), expectedType.getTypeSignature(), types.get(Symbol.from(symbolReference)).getTypeSignature());
                    continue;
                }
                Type actualType = typeAnalyzer.getType(session, types, entry.getValue());
                verifyTypeSignature(entry.getKey(), expectedType.getTypeSignature(), actualType.getTypeSignature());
            }

            return null;
        }

        @Override
        public Void visitUnion(UnionNode node, Void context)
        {
            visitPlan(node, context);

            ListMultimap<Symbol, Symbol> symbolMapping = node.getSymbolMapping();
            for (Symbol keySymbol : symbolMapping.keySet()) {
                List<Symbol> valueSymbols = symbolMapping.get(keySymbol);
                Type expectedType = types.get(keySymbol);
                for (Symbol valueSymbol : valueSymbols) {
                    verifyTypeSignature(keySymbol, expectedType.getTypeSignature(), types.get(valueSymbol).getTypeSignature());
                }
            }

            return null;
        }

        private void checkWindowFunctions(Map<Symbol, WindowNode.Function> functions)
        {
            for (Map.Entry<Symbol, WindowNode.Function> entry : functions.entrySet()) {
                Signature signature = entry.getValue().getSignature();
                FunctionCall call = entry.getValue().getFunctionCall();

                checkSignature(entry.getKey(), signature);
                checkCall(entry.getKey(), call);
            }
        }

        private void checkSignature(Symbol symbol, Signature signature)
        {
            TypeSignature expectedTypeSignature = types.get(symbol).getTypeSignature();
            TypeSignature actualTypeSignature = signature.getReturnType();
            verifyTypeSignature(symbol, expectedTypeSignature, actualTypeSignature);
        }

        private void checkCall(Symbol symbol, FunctionCall call)
        {
            Type expectedType = types.get(symbol);
            Type actualType = typeAnalyzer.getType(session, types, call);
            verifyTypeSignature(symbol, expectedType.getTypeSignature(), actualType.getTypeSignature());
        }

        private void checkFunctionSignature(Map<Symbol, Aggregation> aggregations)
        {
            for (Map.Entry<Symbol, Aggregation> entry : aggregations.entrySet()) {
                checkSignature(entry.getKey(), entry.getValue().getSignature());
            }
        }

        private void checkFunctionCall(Map<Symbol, Aggregation> aggregations)
        {
            for (Map.Entry<Symbol, Aggregation> entry : aggregations.entrySet()) {
                checkCall(entry.getKey(), entry.getValue().getCall());
            }
        }

        private void verifyTypeSignature(Symbol symbol, TypeSignature expected, TypeSignature actual)
        {
            // UNKNOWN should be considered as a wildcard type, which matches all the other types
            TypeManager typeManager = metadata.getTypeManager();
            if (!actual.equals(UNKNOWN.getTypeSignature()) && !typeManager.isTypeOnlyCoercion(typeManager.getType(actual), typeManager.getType(expected))) {
                checkArgument(expected.equals(actual), "type of symbol '%s' is expected to be %s, but the actual type is %s", symbol, expected, actual);
            }
        }
    }
}
