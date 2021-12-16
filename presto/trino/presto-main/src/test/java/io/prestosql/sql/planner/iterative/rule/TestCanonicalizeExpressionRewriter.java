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

import io.prestosql.sql.planner.iterative.rule.test.PlanBuilder;
import org.testng.annotations.Test;

import static io.prestosql.sql.planner.iterative.rule.CanonicalizeExpressionRewriter.canonicalizeExpression;
import static org.testng.Assert.assertEquals;

public class TestCanonicalizeExpressionRewriter
{
    @Test
    public void testRewriteIsNotNullPredicate()
    {
        assertRewritten("x is NOT NULL", "NOT (x IS NULL)");
    }

    @Test
    public void testRewriteIfExpression()
    {
        assertRewritten("IF(x = 0, 0, 1)", "CASE WHEN x = 0 THEN 0 ELSE 1 END");
    }

    @Test
    public void testRewriteCurrentTime()
    {
        assertRewritten("CURRENT_TIME", "\"current_time\"()");
    }

    @Test
    public void testRewriteYearExtract()
    {
        assertRewritten("EXTRACT(YEAR FROM '2017-07-20')", "year('2017-07-20')");
    }

    @Test
    public void testCanonicalizeArithmetic()
    {
        assertRewritten("a + 1", "a + 1");
        assertRewritten("1 + a", "a + 1");

        assertRewritten("a * 1", "a * 1");
        assertRewritten("1 * a", "a * 1");
    }

    @Test
    public void testCanonicalizeComparison()
    {
        assertRewritten("a = 1", "a = 1");
        assertRewritten("1 = a", "a = 1");

        assertRewritten("a <> 1", "a <> 1");
        assertRewritten("1 <> a", "a <> 1");

        assertRewritten("a > 1", "a > 1");
        assertRewritten("1 > a", "a < 1");

        assertRewritten("a < 1", "a < 1");
        assertRewritten("1 < a", "a > 1");

        assertRewritten("a >= 1", "a >= 1");
        assertRewritten("1 >= a", "a <= 1");

        assertRewritten("a <= 1", "a <= 1");
        assertRewritten("1 <= a", "a >= 1");

        assertRewritten("a IS DISTINCT FROM 1", "a IS DISTINCT FROM 1");
        assertRewritten("1 IS DISTINCT FROM a", "a IS DISTINCT FROM 1");
    }

    @Test
    public void testTypedLiteral()
    {
        // typed literals are encoded as Cast(Literal) in current IR

        assertRewritten("a = CAST(1 AS decimal(5,2))", "a = CAST(1 AS decimal(5,2))");
        assertRewritten("CAST(1 AS decimal(5,2)) = a", "a = CAST(1 AS decimal(5,2))");

        assertRewritten("a + CAST(1 AS decimal(5,2))", "a + CAST(1 AS decimal(5,2))");
        assertRewritten("CAST(1 AS decimal(5,2)) + a", "a + CAST(1 AS decimal(5,2))");
    }

    private static void assertRewritten(String from, String to)
    {
        assertEquals(canonicalizeExpression(PlanBuilder.expression(from)), PlanBuilder.expression(to));
    }
}
