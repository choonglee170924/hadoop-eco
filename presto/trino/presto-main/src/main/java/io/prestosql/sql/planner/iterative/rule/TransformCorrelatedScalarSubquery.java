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
package io.prestosql.sql.planner.iterative.rule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import io.prestosql.matching.Captures;
import io.prestosql.matching.Pattern;
import io.prestosql.spi.type.BigintType;
import io.prestosql.spi.type.BooleanType;
import io.prestosql.sql.planner.Symbol;
import io.prestosql.sql.planner.iterative.Rule;
import io.prestosql.sql.planner.plan.AssignUniqueId;
import io.prestosql.sql.planner.plan.Assignments;
import io.prestosql.sql.planner.plan.EnforceSingleRowNode;
import io.prestosql.sql.planner.plan.FilterNode;
import io.prestosql.sql.planner.plan.LateralJoinNode;
import io.prestosql.sql.planner.plan.MarkDistinctNode;
import io.prestosql.sql.planner.plan.PlanNode;
import io.prestosql.sql.planner.plan.ProjectNode;
import io.prestosql.sql.tree.Cast;
import io.prestosql.sql.tree.FunctionCall;
import io.prestosql.sql.tree.LongLiteral;
import io.prestosql.sql.tree.QualifiedName;
import io.prestosql.sql.tree.SimpleCaseExpression;
import io.prestosql.sql.tree.StringLiteral;
import io.prestosql.sql.tree.WhenClause;

import java.util.Optional;

import static io.prestosql.matching.Pattern.nonEmpty;
import static io.prestosql.spi.StandardErrorCode.SUBQUERY_MULTIPLE_ROWS;
import static io.prestosql.spi.type.StandardTypes.BOOLEAN;
import static io.prestosql.sql.planner.optimizations.PlanNodeSearcher.searchFrom;
import static io.prestosql.sql.planner.optimizations.QueryCardinalityUtil.extractCardinality;
import static io.prestosql.sql.planner.plan.LateralJoinNode.Type.LEFT;
import static io.prestosql.sql.planner.plan.Patterns.LateralJoin.correlation;
import static io.prestosql.sql.planner.plan.Patterns.LateralJoin.filter;
import static io.prestosql.sql.planner.plan.Patterns.lateralJoin;
import static io.prestosql.sql.tree.BooleanLiteral.TRUE_LITERAL;

/**
 * Scalar filter scan query is something like:
 * <pre>
 *     SELECT a,b,c FROM rel WHERE a = correlated1 AND b = correlated2
 * </pre>
 * <p>
 * This optimizer can rewrite to mark distinct and filter over a left outer join:
 * <p>
 * From:
 * <pre>
 * - LateralJoin (with correlation list: [C])
 *   - (input) plan which produces symbols: [A, B, C]
 *   - (scalar subquery) Project F
 *     - Filter(D = C AND E > 5)
 *       - plan which produces symbols: [D, E, F]
 * </pre>
 * to:
 * <pre>
 * - Filter(CASE isDistinct WHEN true THEN true ELSE fail('Scalar sub-query has returned multiple rows'))
 *   - MarkDistinct(isDistinct)
 *     - LateralJoin (with correlation list: [C])
 *       - AssignUniqueId(adds symbol U)
 *         - (input) plan which produces symbols: [A, B, C]
 *       - non scalar subquery
 * </pre>
 * <p>
 * This must be run after {@link TransformCorrelatedScalarAggregationToJoin}
 */
public class TransformCorrelatedScalarSubquery
        implements Rule<LateralJoinNode>
{
    private static final Pattern<LateralJoinNode> PATTERN = lateralJoin()
            .with(nonEmpty(correlation()))
            .with(filter().equalTo(TRUE_LITERAL));

    @Override
    public Pattern<LateralJoinNode> getPattern()
    {
        return PATTERN;
    }

    @Override
    public Result apply(LateralJoinNode lateralJoinNode, Captures captures, Context context)
    {
        PlanNode subquery = context.getLookup().resolve(lateralJoinNode.getSubquery());

        if (!searchFrom(subquery, context.getLookup())
                .where(EnforceSingleRowNode.class::isInstance)
                .recurseOnlyWhen(ProjectNode.class::isInstance)
                .matches()) {
            return Result.empty();
        }

        PlanNode rewrittenSubquery = searchFrom(subquery, context.getLookup())
                .where(EnforceSingleRowNode.class::isInstance)
                .recurseOnlyWhen(ProjectNode.class::isInstance)
                .removeFirst();

        Range<Long> subqueryCardinality = extractCardinality(rewrittenSubquery, context.getLookup());
        boolean producesAtMostOneRow = Range.closed(0L, 1L).encloses(subqueryCardinality);
        if (producesAtMostOneRow) {
            boolean producesSingleRow = Range.singleton(1L).encloses(subqueryCardinality);
            return Result.ofPlanNode(new LateralJoinNode(
                    context.getIdAllocator().getNextId(),
                    lateralJoinNode.getInput(),
                    rewrittenSubquery,
                    lateralJoinNode.getCorrelation(),
                    producesSingleRow ? lateralJoinNode.getType() : LEFT,
                    lateralJoinNode.getFilter(),
                    lateralJoinNode.getOriginSubquery()));
        }

        Symbol unique = context.getSymbolAllocator().newSymbol("unique", BigintType.BIGINT);

        LateralJoinNode rewrittenLateralJoinNode = new LateralJoinNode(
                context.getIdAllocator().getNextId(),
                new AssignUniqueId(
                        context.getIdAllocator().getNextId(),
                        lateralJoinNode.getInput(),
                        unique),
                rewrittenSubquery,
                lateralJoinNode.getCorrelation(),
                LEFT,
                lateralJoinNode.getFilter(),
                lateralJoinNode.getOriginSubquery());

        Symbol isDistinct = context.getSymbolAllocator().newSymbol("is_distinct", BooleanType.BOOLEAN);
        MarkDistinctNode markDistinctNode = new MarkDistinctNode(
                context.getIdAllocator().getNextId(),
                rewrittenLateralJoinNode,
                isDistinct,
                rewrittenLateralJoinNode.getInput().getOutputSymbols(),
                Optional.empty());

        FilterNode filterNode = new FilterNode(
                context.getIdAllocator().getNextId(),
                markDistinctNode,
                new SimpleCaseExpression(
                        isDistinct.toSymbolReference(),
                        ImmutableList.of(
                                new WhenClause(TRUE_LITERAL, TRUE_LITERAL)),
                        Optional.of(new Cast(
                                new FunctionCall(
                                        QualifiedName.of("fail"),
                                        ImmutableList.of(
                                                new LongLiteral(Integer.toString(SUBQUERY_MULTIPLE_ROWS.toErrorCode().getCode())),
                                                new StringLiteral("Scalar sub-query has returned multiple rows"))),
                                BOOLEAN))));

        return Result.ofPlanNode(new ProjectNode(
                context.getIdAllocator().getNextId(),
                filterNode,
                Assignments.identity(lateralJoinNode.getOutputSymbols())));
    }
}
