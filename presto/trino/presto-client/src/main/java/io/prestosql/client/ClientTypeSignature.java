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
package io.prestosql.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.Immutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.prestosql.client.ClientStandardTypes.ROW;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@Immutable
public class ClientTypeSignature
{
    private static final Pattern PATTERN = Pattern.compile(".*[<>,].*");
    private final String rawType;
    private final List<ClientTypeSignatureParameter> arguments;

    public ClientTypeSignature(String rawType)
    {
        this(rawType, ImmutableList.of());
    }

    public ClientTypeSignature(String rawType, List<ClientTypeSignatureParameter> arguments)
    {
        this(rawType, ImmutableList.of(), ImmutableList.of(), arguments);
    }

    @JsonCreator
    public ClientTypeSignature(
            @JsonProperty("rawType") String rawType,
            @JsonProperty("typeArguments") List<ClientTypeSignature> typeArguments,
            @JsonProperty("literalArguments") List<Object> literalArguments,
            @JsonProperty("arguments") List<ClientTypeSignatureParameter> arguments)
    {
        requireNonNull(rawType, "rawType is null");
        this.rawType = rawType;
        checkArgument(!rawType.isEmpty(), "rawType is empty");
        checkArgument(!PATTERN.matcher(rawType).matches(), "Bad characters in rawType type: %s", rawType);
        if (arguments != null) {
            this.arguments = unmodifiableList(new ArrayList<>(arguments));
        }
        else {
            requireNonNull(typeArguments, "typeArguments is null");
            requireNonNull(literalArguments, "literalArguments is null");
            ImmutableList.Builder<ClientTypeSignatureParameter> convertedArguments = ImmutableList.builder();
            // Talking to a legacy server (< 0.133)
            if (rawType.equals(ROW)) {
                checkArgument(typeArguments.size() == literalArguments.size());
                for (int i = 0; i < typeArguments.size(); i++) {
                    Object value = literalArguments.get(i);
                    checkArgument(value instanceof String, "Expected literalArgument %s in %s to be a string", i, literalArguments);
                    convertedArguments.add(ClientTypeSignatureParameter.ofNamedType(new NamedClientTypeSignature(
                            Optional.of(new RowFieldName((String) value, false)),
                            typeArguments.get(i))));
                }
            }
            else {
                checkArgument(literalArguments.isEmpty(), "Unexpected literal arguments from legacy server");
                for (ClientTypeSignature typeArgument : typeArguments) {
                    convertedArguments.add(new ClientTypeSignatureParameter(ParameterKind.TYPE, typeArgument));
                }
            }
            this.arguments = convertedArguments.build();
        }
    }

    @JsonProperty
    public String getRawType()
    {
        return rawType;
    }

    @JsonProperty
    public List<ClientTypeSignatureParameter> getArguments()
    {
        return arguments;
    }

    public List<ClientTypeSignature> getArgumentsAsTypeSignatures()
    {
        return arguments.stream()
                .peek(parameter -> checkState(parameter.getKind() == ParameterKind.TYPE,
                        "Expected all parameters to be TypeSignatures but [%s] was found", parameter))
                .map(ClientTypeSignatureParameter::getTypeSignature)
                .collect(toImmutableList());
    }

    /**
     * This field is deprecated and clients should switch to {@link #getArguments()}
     */
    @Deprecated
    @JsonProperty
    public List<ClientTypeSignature> getTypeArguments()
    {
        List<ClientTypeSignature> result = new ArrayList<>();
        for (ClientTypeSignatureParameter argument : arguments) {
            switch (argument.getKind()) {
                case TYPE:
                    result.add(argument.getTypeSignature());
                    break;
                case NAMED_TYPE:
                    result.add(argument.getNamedTypeSignature().getTypeSignature());
                    break;
                default:
                    return new ArrayList<>();
            }
        }
        return result;
    }

    /**
     * This field is deprecated and clients should switch to {@link #getArguments()}
     */
    @Deprecated
    @JsonProperty
    public List<Object> getLiteralArguments()
    {
        List<Object> result = new ArrayList<>();
        for (ClientTypeSignatureParameter argument : arguments) {
            switch (argument.getKind()) {
                case NAMED_TYPE:
                    result.add(argument.getNamedTypeSignature().getName());
                    break;
                default:
                    return new ArrayList<>();
            }
        }
        return result;
    }

    @Override
    public String toString()
    {
        if (rawType.equals(ROW)) {
            return rowToString();
        }

        if (arguments.isEmpty()) {
            return rawType;
        }
        return rawType + arguments.stream()
                .map(ClientTypeSignatureParameter::toString)
                .collect(joining(",", "(", ")"));
    }

    @Deprecated
    private String rowToString()
    {
        String fields = arguments.stream()
                .map(ClientTypeSignatureParameter::getNamedTypeSignature)
                .map(parameter -> {
                    if (parameter.getName().isPresent()) {
                        return format("%s %s", parameter.getName().get(), parameter.getTypeSignature().toString());
                    }
                    return parameter.getTypeSignature().toString();
                })
                .collect(joining(","));

        return format("row(%s)", fields);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClientTypeSignature other = (ClientTypeSignature) o;

        return Objects.equals(this.rawType.toLowerCase(Locale.ENGLISH), other.rawType.toLowerCase(Locale.ENGLISH)) &&
                Objects.equals(this.arguments, other.arguments);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(rawType.toLowerCase(Locale.ENGLISH), arguments);
    }
}
