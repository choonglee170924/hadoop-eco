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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.prestosql.metadata.Signature;
import io.prestosql.spi.function.OperatorType;
import io.prestosql.spi.type.CharType;
import io.prestosql.spi.type.StandardTypes;
import io.prestosql.spi.type.Type;
import io.prestosql.spi.type.TypeSignature;
import io.prestosql.sql.tree.ArithmeticBinaryExpression;
import io.prestosql.sql.tree.ComparisonExpression;
import io.prestosql.sql.tree.LogicalBinaryExpression;
import io.prestosql.type.LikePatternType;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static io.prestosql.metadata.FunctionKind.SCALAR;
import static io.prestosql.metadata.FunctionRegistry.mangleOperatorName;
import static io.prestosql.metadata.Signature.internalOperator;
import static io.prestosql.metadata.Signature.internalScalarFunction;
import static io.prestosql.spi.function.OperatorType.SUBSCRIPT;
import static io.prestosql.spi.type.TypeSignature.parseTypeSignature;
import static io.prestosql.sql.tree.ArrayConstructor.ARRAY_CONSTRUCTOR;

public final class Signatures
{
    public static final String CAST = mangleOperatorName("CAST");

    private Signatures()
    {
    }

    // **************** sql operators ****************
    public static Signature notSignature()
    {
        return new Signature("not", SCALAR, parseTypeSignature(StandardTypes.BOOLEAN), ImmutableList.of(parseTypeSignature(StandardTypes.BOOLEAN)));
    }

    public static Signature betweenSignature(Type valueType, Type minType, Type maxType)
    {
        return internalOperator("BETWEEN", parseTypeSignature(StandardTypes.BOOLEAN), valueType.getTypeSignature(), minType.getTypeSignature(), maxType.getTypeSignature());
    }

    public static Signature likeVarcharSignature()
    {
        return internalScalarFunction("LIKE", parseTypeSignature(StandardTypes.BOOLEAN), parseTypeSignature(StandardTypes.VARCHAR), parseTypeSignature(LikePatternType.NAME));
    }

    public static Signature likeCharSignature(Type valueType)
    {
        checkArgument(valueType instanceof CharType, "Expected CHAR value type");
        return internalScalarFunction("LIKE", parseTypeSignature(StandardTypes.BOOLEAN), valueType.getTypeSignature(), parseTypeSignature(LikePatternType.NAME));
    }

    public static Signature likePatternSignature()
    {
        return internalScalarFunction("LIKE_PATTERN", parseTypeSignature(LikePatternType.NAME), parseTypeSignature(StandardTypes.VARCHAR), parseTypeSignature(StandardTypes.VARCHAR));
    }

    public static Signature castSignature(Type returnType, Type valueType)
    {
        // Name has already been mangled, so don't use internalOperator
        return internalScalarFunction(CAST, returnType.getTypeSignature(), valueType.getTypeSignature());
    }

    public static Signature tryCastSignature(Type returnType, Type valueType)
    {
        return internalScalarFunction("TRY_CAST", returnType.getTypeSignature(), valueType.getTypeSignature());
    }

    public static Signature logicalExpressionSignature(LogicalBinaryExpression.Operator operator)
    {
        return internalScalarFunction(operator.name(), parseTypeSignature(StandardTypes.BOOLEAN), parseTypeSignature(StandardTypes.BOOLEAN), parseTypeSignature(StandardTypes.BOOLEAN));
    }

    public static Signature arithmeticNegationSignature(Type returnType, Type valueType)
    {
        return internalOperator("NEGATION", returnType.getTypeSignature(), valueType.getTypeSignature());
    }

    public static Signature arithmeticExpressionSignature(ArithmeticBinaryExpression.Operator operator, Type returnType, Type leftType, Type rightType)
    {
        return internalOperator(operator.name(), returnType.getTypeSignature(), leftType.getTypeSignature(), rightType.getTypeSignature());
    }

    public static Signature subscriptSignature(Type returnType, Type leftType, Type rightType)
    {
        return internalOperator(SUBSCRIPT.name(), returnType.getTypeSignature(), leftType.getTypeSignature(), rightType.getTypeSignature());
    }

    public static Signature arrayConstructorSignature(Type returnType, List<? extends Type> argumentTypes)
    {
        return internalScalarFunction(ARRAY_CONSTRUCTOR, returnType.getTypeSignature(), Lists.transform(argumentTypes, Type::getTypeSignature));
    }

    public static Signature arrayConstructorSignature(TypeSignature returnType, List<TypeSignature> argumentTypes)
    {
        return internalScalarFunction(ARRAY_CONSTRUCTOR, returnType, argumentTypes);
    }

    public static Signature comparisonExpressionSignature(ComparisonExpression.Operator operator, Type leftType, Type rightType)
    {
        for (OperatorType operatorType : OperatorType.values()) {
            if (operatorType.name().equals(operator.name())) {
                return internalOperator(operator.name(), parseTypeSignature(StandardTypes.BOOLEAN), leftType.getTypeSignature(), rightType.getTypeSignature());
            }
        }
        return internalScalarFunction(operator.name(), parseTypeSignature(StandardTypes.BOOLEAN), leftType.getTypeSignature(), rightType.getTypeSignature());
    }

    public static Signature trySignature(Type returnType)
    {
        return new Signature("TRY", SCALAR, returnType.getTypeSignature());
    }
}
