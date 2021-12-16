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
package io.prestosql.connector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.prestosql.plugin.tpch.TpchColumnHandle;
import io.prestosql.plugin.tpch.TpchHandleResolver;
import io.prestosql.plugin.tpch.TpchRecordSetProvider;
import io.prestosql.plugin.tpch.TpchSplitManager;
import io.prestosql.spi.connector.ColumnHandle;
import io.prestosql.spi.connector.ColumnMetadata;
import io.prestosql.spi.connector.Connector;
import io.prestosql.spi.connector.ConnectorContext;
import io.prestosql.spi.connector.ConnectorFactory;
import io.prestosql.spi.connector.ConnectorHandleResolver;
import io.prestosql.spi.connector.ConnectorMetadata;
import io.prestosql.spi.connector.ConnectorRecordSetProvider;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.connector.ConnectorSplitManager;
import io.prestosql.spi.connector.ConnectorTableHandle;
import io.prestosql.spi.connector.ConnectorTableLayout;
import io.prestosql.spi.connector.ConnectorTableLayoutHandle;
import io.prestosql.spi.connector.ConnectorTableLayoutResult;
import io.prestosql.spi.connector.ConnectorTableMetadata;
import io.prestosql.spi.connector.ConnectorTransactionHandle;
import io.prestosql.spi.connector.ConnectorViewDefinition;
import io.prestosql.spi.connector.Constraint;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.connector.SchemaTablePrefix;
import io.prestosql.spi.predicate.TupleDomain;
import io.prestosql.spi.transaction.IsolationLevel;
import io.prestosql.testing.TestingHandle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.prestosql.spi.type.VarcharType.createUnboundedVarcharType;
import static java.util.Objects.requireNonNull;

public class MockConnectorFactory
        implements ConnectorFactory
{
    private final Function<ConnectorSession, List<String>> listSchemaNames;
    private final BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables;
    private final BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews;
    private final BiFunction<ConnectorSession, ConnectorTableHandle, Map<String, TpchColumnHandle>> getColumnHandles;

    private MockConnectorFactory(
            Function<ConnectorSession, List<String>> listSchemaNames,
            BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables,
            BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews,
            BiFunction<ConnectorSession, ConnectorTableHandle, Map<String, TpchColumnHandle>> getColumnHandles)
    {
        this.listSchemaNames = requireNonNull(listSchemaNames, "listSchemaNames is null");
        this.listTables = requireNonNull(listTables, "listTables is null");
        this.getViews = requireNonNull(getViews, "getViews is null");
        this.getColumnHandles = requireNonNull(getColumnHandles, "getColumnHandles is null");
    }

    @Override
    public String getName()
    {
        return "mock";
    }

    @Override
    public ConnectorHandleResolver getHandleResolver()
    {
        return new TpchHandleResolver();
    }

    @Override
    public Connector create(String catalogName, Map<String, String> config, ConnectorContext context)
    {
        return new MockConnector(context, listSchemaNames, listTables, getViews, getColumnHandles);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    private static class MockConnector
            implements Connector
    {
        private final ConnectorContext context;
        private final Function<ConnectorSession, List<String>> listSchemaNames;
        private final BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables;
        private final BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews;
        private final BiFunction<ConnectorSession, ConnectorTableHandle, Map<String, TpchColumnHandle>> getColumnHandles;

        public MockConnector(
                ConnectorContext context,
                Function<ConnectorSession, List<String>> listSchemaNames,
                BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables,
                BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews,
                BiFunction<ConnectorSession, ConnectorTableHandle, Map<String, TpchColumnHandle>> getColumnHandles)
        {
            this.context = requireNonNull(context, "context is null");
            this.listSchemaNames = requireNonNull(listSchemaNames, "listSchemaNames is null");
            this.listTables = requireNonNull(listTables, "listTables is null");
            this.getViews = requireNonNull(getViews, "getViews is null");
            this.getColumnHandles = requireNonNull(getColumnHandles, "getColumnHandles is null");
        }

        @Override
        public ConnectorTransactionHandle beginTransaction(IsolationLevel isolationLevel, boolean readOnly)
        {
            return new ConnectorTransactionHandle() {};
        }

        @Override
        public ConnectorMetadata getMetadata(ConnectorTransactionHandle transaction)
        {
            return new MockConnectorMetadata();
        }

        @Override
        public ConnectorSplitManager getSplitManager()
        {
            return new TpchSplitManager(context.getNodeManager(), 1);
        }

        @Override
        public ConnectorRecordSetProvider getRecordSetProvider()
        {
            return new TpchRecordSetProvider();
        }

        private class MockConnectorMetadata
                implements ConnectorMetadata
        {
            @Override
            public List<String> listSchemaNames(ConnectorSession session)
            {
                return listSchemaNames.apply(session);
            }

            @Override
            public ConnectorTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName)
            {
                return new ConnectorTableHandle() {};
            }

            @Override
            public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle table)
            {
                return null;
            }

            @Override
            public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> schemaName)
            {
                return listTables.apply(session, schemaName.orElse(null));
            }

            @Override
            public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
            {
                return ImmutableMap.copyOf(getColumnHandles.apply(session, tableHandle));
            }

            @Override
            public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
            {
                TpchColumnHandle tpchColumnHandle = (TpchColumnHandle) columnHandle;
                return new ColumnMetadata(tpchColumnHandle.getColumnName(), tpchColumnHandle.getType());
            }

            @Override
            public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(ConnectorSession session, SchemaTablePrefix prefix)
            {
                return listTables(session, prefix.getSchema()).stream()
                        .collect(toImmutableMap(table -> table, table -> IntStream.range(0, 100)
                                .boxed()
                                .map(i -> new ColumnMetadata("column_" + i, createUnboundedVarcharType()))
                                .collect(toImmutableList())));
            }

            @Override
            public List<ConnectorTableLayoutResult> getTableLayouts(ConnectorSession session, ConnectorTableHandle table, Constraint constraint, Optional<Set<ColumnHandle>> desiredColumns)
            {
                return ImmutableList.of(new ConnectorTableLayoutResult(new ConnectorTableLayout(TestingHandle.INSTANCE), TupleDomain.all()));
            }

            @Override
            public ConnectorTableLayout getTableLayout(ConnectorSession session, ConnectorTableLayoutHandle handle)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<SchemaTableName, ConnectorViewDefinition> getViews(ConnectorSession session, Optional<String> schemaName)
            {
                return getViews.apply(session, schemaName.map(SchemaTablePrefix::new).orElseGet(SchemaTablePrefix::new));
            }

            @Override
            public Optional<ConnectorViewDefinition> getView(ConnectorSession session, SchemaTableName viewName)
            {
                return Optional.of(getViews.apply(session, viewName.toSchemaTablePrefix()).get(viewName));
            }
        }
    }

    public static final class Builder
    {
        private Function<ConnectorSession, List<String>> listSchemaNames = (session) -> ImmutableList.of();
        private BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables = (session, schemaName) -> ImmutableList.of();
        private BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews = (session, schemaTablePrefix) -> ImmutableMap.of();
        private BiFunction<ConnectorSession, ConnectorTableHandle, Map<String, TpchColumnHandle>> getColumnHandles = (session, tableHandle) -> notSupported();

        public Builder withListSchemaNames(Function<ConnectorSession, List<String>> listSchemaNames)
        {
            this.listSchemaNames = requireNonNull(listSchemaNames, "listSchemaNames is null");
            return this;
        }

        public Builder withListTables(BiFunction<ConnectorSession, String, List<SchemaTableName>> listTables)
        {
            this.listTables = requireNonNull(listTables, "listTables is null");
            return this;
        }

        public Builder withGetViews(BiFunction<ConnectorSession, SchemaTablePrefix, Map<SchemaTableName, ConnectorViewDefinition>> getViews)
        {
            this.getViews = requireNonNull(getViews, "getViews is null");
            return this;
        }

        public Builder withGetColumnHandles(BiFunction<ConnectorSession, ConnectorTableHandle, Map<String, TpchColumnHandle>> getColumnHandles)
        {
            this.getColumnHandles = requireNonNull(getColumnHandles, "getColumnHandles is null");
            return this;
        }

        public MockConnectorFactory build()
        {
            return new MockConnectorFactory(listSchemaNames, listTables, getViews, getColumnHandles);
        }

        private static <T> T notSupported()
        {
            throw new UnsupportedOperationException();
        }
    }
}
