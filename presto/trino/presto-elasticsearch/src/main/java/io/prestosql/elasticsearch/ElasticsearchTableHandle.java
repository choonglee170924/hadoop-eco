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
package io.prestosql.elasticsearch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import io.prestosql.spi.connector.ColumnHandle;
import io.prestosql.spi.connector.ConnectorTableHandle;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.predicate.TupleDomain;

import java.util.Objects;

import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public final class ElasticsearchTableHandle
        implements ConnectorTableHandle
{
    private final SchemaTableName schemaTableName;
    private final TupleDomain<ColumnHandle> constraint;

    public ElasticsearchTableHandle(String schemaName, String tableName)
    {
        this(schemaName, tableName, TupleDomain.all());
    }

    @JsonCreator
    public ElasticsearchTableHandle(
            @JsonProperty("schemaName") String schemaName,
            @JsonProperty("tableName") String tableName,
            @JsonProperty("constraint") TupleDomain<ColumnHandle> constraint)
    {
        requireNonNull(schemaName, "schemaName is null");
        requireNonNull(tableName, "tableName is null");
        this.schemaTableName = new SchemaTableName(schemaName.toLowerCase(ENGLISH), tableName.toLowerCase(ENGLISH));
        this.constraint = requireNonNull(constraint, "constraint is null");
    }

    @JsonProperty
    public String getSchemaName()
    {
        return schemaTableName.getSchemaName();
    }

    @JsonProperty
    public String getTableName()
    {
        return schemaTableName.getTableName();
    }

    @JsonProperty
    public TupleDomain<ColumnHandle> getConstraint()
    {
        return constraint;
    }

    public SchemaTableName getSchemaTableName()
    {
        return schemaTableName;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getSchemaName(), getTableName());
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        ElasticsearchTableHandle other = (ElasticsearchTableHandle) obj;
        return Objects.equals(this.getSchemaName(), other.getSchemaName()) &&
                Objects.equals(this.getTableName(), other.getTableName());
    }

    @Override
    public String toString()
    {
        return Joiner.on(":").join(getSchemaName(), getTableName());
    }
}
