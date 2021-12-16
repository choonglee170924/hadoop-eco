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
package io.prestosql.connector.informationschema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.slice.Slice;
import io.prestosql.FullConnectorSession;
import io.prestosql.Session;
import io.prestosql.metadata.Metadata;
import io.prestosql.metadata.QualifiedObjectName;
import io.prestosql.metadata.QualifiedTablePrefix;
import io.prestosql.spi.connector.ColumnHandle;
import io.prestosql.spi.connector.ColumnMetadata;
import io.prestosql.spi.connector.ConnectorMetadata;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.connector.ConnectorTableHandle;
import io.prestosql.spi.connector.ConnectorTableLayout;
import io.prestosql.spi.connector.ConnectorTableLayoutHandle;
import io.prestosql.spi.connector.ConnectorTableMetadata;
import io.prestosql.spi.connector.ConnectorTableProperties;
import io.prestosql.spi.connector.Constraint;
import io.prestosql.spi.connector.ConstraintApplicationResult;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.connector.SchemaTablePrefix;
import io.prestosql.spi.predicate.Domain;
import io.prestosql.spi.predicate.EquatableValueSet;
import io.prestosql.spi.predicate.NullableValue;
import io.prestosql.spi.predicate.TupleDomain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.compose;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.airlift.slice.Slices.utf8Slice;
import static io.prestosql.metadata.MetadataUtil.SchemaMetadataBuilder.schemaMetadataBuilder;
import static io.prestosql.metadata.MetadataUtil.TableMetadataBuilder.tableMetadataBuilder;
import static io.prestosql.metadata.MetadataUtil.findColumnMetadata;
import static io.prestosql.spi.type.BigintType.BIGINT;
import static io.prestosql.spi.type.VarcharType.createUnboundedVarcharType;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class InformationSchemaMetadata
        implements ConnectorMetadata
{
    public static final String INFORMATION_SCHEMA = "information_schema";

    public static final SchemaTableName TABLE_COLUMNS = new SchemaTableName(INFORMATION_SCHEMA, "columns");
    public static final SchemaTableName TABLE_TABLES = new SchemaTableName(INFORMATION_SCHEMA, "tables");
    public static final SchemaTableName TABLE_VIEWS = new SchemaTableName(INFORMATION_SCHEMA, "views");
    public static final SchemaTableName TABLE_SCHEMATA = new SchemaTableName(INFORMATION_SCHEMA, "schemata");
    public static final SchemaTableName TABLE_TABLE_PRIVILEGES = new SchemaTableName(INFORMATION_SCHEMA, "table_privileges");
    public static final SchemaTableName TABLE_ROLES = new SchemaTableName(INFORMATION_SCHEMA, "roles");
    public static final SchemaTableName TABLE_APPLICABLE_ROLES = new SchemaTableName(INFORMATION_SCHEMA, "applicable_roles");
    public static final SchemaTableName TABLE_ENABLED_ROLES = new SchemaTableName(INFORMATION_SCHEMA, "enabled_roles");

    public static final Map<SchemaTableName, ConnectorTableMetadata> TABLES = schemaMetadataBuilder()
            .table(tableMetadataBuilder(TABLE_COLUMNS)
                    .column("table_catalog", createUnboundedVarcharType())
                    .column("table_schema", createUnboundedVarcharType())
                    .column("table_name", createUnboundedVarcharType())
                    .column("column_name", createUnboundedVarcharType())
                    .column("ordinal_position", BIGINT)
                    .column("column_default", createUnboundedVarcharType())
                    .column("is_nullable", createUnboundedVarcharType())
                    .column("data_type", createUnboundedVarcharType())
                    .column("comment", createUnboundedVarcharType())
                    .column("extra_info", createUnboundedVarcharType())
                    .hiddenColumn("column_comment", createUnboundedVarcharType()) // MySQL compatible
                    .build())
            .table(tableMetadataBuilder(TABLE_TABLES)
                    .column("table_catalog", createUnboundedVarcharType())
                    .column("table_schema", createUnboundedVarcharType())
                    .column("table_name", createUnboundedVarcharType())
                    .column("table_type", createUnboundedVarcharType())
                    .hiddenColumn("table_comment", createUnboundedVarcharType()) // MySQL compatible
                    .build())
            .table(tableMetadataBuilder(TABLE_VIEWS)
                    .column("table_catalog", createUnboundedVarcharType())
                    .column("table_schema", createUnboundedVarcharType())
                    .column("table_name", createUnboundedVarcharType())
                    .column("view_definition", createUnboundedVarcharType())
                    .build())
            .table(tableMetadataBuilder(TABLE_SCHEMATA)
                    .column("catalog_name", createUnboundedVarcharType())
                    .column("schema_name", createUnboundedVarcharType())
                    .build())
            .table(tableMetadataBuilder(TABLE_TABLE_PRIVILEGES)
                    .column("grantor", createUnboundedVarcharType())
                    .column("grantor_type", createUnboundedVarcharType())
                    .column("grantee", createUnboundedVarcharType())
                    .column("grantee_type", createUnboundedVarcharType())
                    .column("table_catalog", createUnboundedVarcharType())
                    .column("table_schema", createUnboundedVarcharType())
                    .column("table_name", createUnboundedVarcharType())
                    .column("privilege_type", createUnboundedVarcharType())
                    .column("is_grantable", createUnboundedVarcharType())
                    .column("with_hierarchy", createUnboundedVarcharType())
                    .build())
            .table(tableMetadataBuilder(TABLE_ROLES)
                    .column("role_name", createUnboundedVarcharType())
                    .build())
            .table(tableMetadataBuilder(TABLE_APPLICABLE_ROLES)
                    .column("grantee", createUnboundedVarcharType())
                    .column("grantee_type", createUnboundedVarcharType())
                    .column("role_name", createUnboundedVarcharType())
                    .column("is_grantable", createUnboundedVarcharType())
                    .build())
            .table(tableMetadataBuilder(TABLE_ENABLED_ROLES)
                    .column("role_name", createUnboundedVarcharType())
                    .build())
            .build();

    private static final InformationSchemaColumnHandle CATALOG_COLUMN_HANDLE = new InformationSchemaColumnHandle("table_catalog");
    private static final InformationSchemaColumnHandle SCHEMA_COLUMN_HANDLE = new InformationSchemaColumnHandle("table_schema");
    private static final InformationSchemaColumnHandle TABLE_NAME_COLUMN_HANDLE = new InformationSchemaColumnHandle("table_name");
    private static final int MAX_PREFIXES_COUNT = 100;

    private final String catalogName;
    private final Metadata metadata;

    public InformationSchemaMetadata(String catalogName, Metadata metadata)
    {
        this.catalogName = requireNonNull(catalogName, "catalogName is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
    }

    private InformationSchemaTableHandle checkTableHandle(ConnectorTableHandle tableHandle)
    {
        InformationSchemaTableHandle handle = (InformationSchemaTableHandle) tableHandle;
        checkArgument(handle.getCatalogName().equals(catalogName), "invalid table handle: expected catalog %s but got %s", catalogName, handle.getCatalogName());
        checkArgument(TABLES.containsKey(handle.getSchemaTableName()), "table %s does not exist", handle.getSchemaTableName());
        return handle;
    }

    @Override
    public List<String> listSchemaNames(ConnectorSession session)
    {
        return ImmutableList.of(INFORMATION_SCHEMA);
    }

    @Override
    public ConnectorTableHandle getTableHandle(ConnectorSession connectorSession, SchemaTableName tableName)
    {
        if (!TABLES.containsKey(tableName)) {
            return null;
        }

        return new InformationSchemaTableHandle(catalogName, tableName.getSchemaName(), tableName.getTableName(), defaultPrefixes());
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        InformationSchemaTableHandle informationSchemaTableHandle = checkTableHandle(tableHandle);
        return TABLES.get(informationSchemaTableHandle.getSchemaTableName());
    }

    @Override
    public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> schemaName)
    {
        if (!schemaName.isPresent()) {
            return ImmutableList.copyOf(TABLES.keySet());
        }

        return TABLES.keySet().stream()
                .filter(compose(schemaName.get()::equals, SchemaTableName::getSchemaName))
                .collect(toImmutableList());
    }

    @Override
    public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
    {
        InformationSchemaTableHandle informationSchemaTableHandle = checkTableHandle(tableHandle);
        ConnectorTableMetadata tableMetadata = TABLES.get(informationSchemaTableHandle.getSchemaTableName());

        String columnName = ((InformationSchemaColumnHandle) columnHandle).getColumnName();

        ColumnMetadata columnMetadata = findColumnMetadata(tableMetadata, columnName);
        checkArgument(columnMetadata != null, "Column %s on table %s does not exist", columnName, tableMetadata.getTable());
        return columnMetadata;
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        InformationSchemaTableHandle informationSchemaTableHandle = checkTableHandle(tableHandle);

        ConnectorTableMetadata tableMetadata = TABLES.get(informationSchemaTableHandle.getSchemaTableName());

        return tableMetadata.getColumns().stream()
                .map(ColumnMetadata::getName)
                .collect(toMap(identity(), InformationSchemaColumnHandle::new));
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(ConnectorSession session, SchemaTablePrefix prefix)
    {
        requireNonNull(prefix, "prefix is null");
        ImmutableMap.Builder<SchemaTableName, List<ColumnMetadata>> builder = ImmutableMap.builder();
        for (Entry<SchemaTableName, ConnectorTableMetadata> entry : TABLES.entrySet()) {
            if (prefix.matches(entry.getKey())) {
                builder.put(entry.getKey(), entry.getValue().getColumns());
            }
        }
        return builder.build();
    }

    @Override
    public boolean usesLegacyTableLayouts()
    {
        return false;
    }

    @Override
    public ConnectorTableProperties getTableProperties(ConnectorSession session, ConnectorTableHandle table)
    {
        return new ConnectorTableProperties();
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(ConnectorSession session, ConnectorTableHandle handle, Constraint constraint)
    {
        InformationSchemaTableHandle table = (InformationSchemaTableHandle) handle;

        if (!table.getPrefixes().equals(defaultPrefixes())) {
            return Optional.empty();
        }

        Set<QualifiedTablePrefix> prefixes = getPrefixes(session, table, constraint);

        table = new InformationSchemaTableHandle(table.getCatalogName(), table.getSchemaName(), table.getTableName(), prefixes);

        return Optional.of(new ConstraintApplicationResult<>(table, constraint.getSummary()));
    }

    private Set<QualifiedTablePrefix> defaultPrefixes()
    {
        return ImmutableSet.of(new QualifiedTablePrefix(catalogName));
    }

    private Set<QualifiedTablePrefix> getPrefixes(ConnectorSession session, InformationSchemaTableHandle table, Constraint constraint)
    {
        InformationSchemaTableHandle handle = checkTableHandle(table);

        if (constraint.getSummary().isNone()) {
            return ImmutableSet.of();
        }

        Set<QualifiedTablePrefix> prefixes = calculatePrefixesWithSchemaName(session, constraint.getSummary(), constraint.predicate());
        if (isTablesEnumeratingTable(handle.getSchemaTableName())) {
            Set<QualifiedTablePrefix> tablePrefixes = calculatePrefixesWithTableName(session, prefixes, constraint.getSummary(), constraint.predicate());
            // in case of high number of prefixes it is better to populate all data and then filter
            if (tablePrefixes.size() <= MAX_PREFIXES_COUNT) {
                prefixes = tablePrefixes;
            }
        }
        if (prefixes.size() > MAX_PREFIXES_COUNT) {
            // in case of high number of prefixes it is better to populate all data and then filter
            prefixes = defaultPrefixes();
        }

        return prefixes;
    }

    private boolean isTablesEnumeratingTable(SchemaTableName schemaTableName)
    {
        return ImmutableSet.of(TABLE_COLUMNS, TABLE_VIEWS, TABLE_TABLES, TABLE_TABLES, TABLE_TABLE_PRIVILEGES).contains(schemaTableName);
    }

    private Set<QualifiedTablePrefix> calculatePrefixesWithSchemaName(
            ConnectorSession connectorSession,
            TupleDomain<ColumnHandle> constraint,
            Optional<Predicate<Map<ColumnHandle, NullableValue>>> predicate)
    {
        Optional<Set<String>> schemas = filterString(constraint, SCHEMA_COLUMN_HANDLE);
        if (schemas.isPresent()) {
            return schemas.get().stream()
                    .filter(this::isLowerCase)
                    .map(schema -> new QualifiedTablePrefix(catalogName, schema))
                    .collect(toImmutableSet());
        }

        Session session = ((FullConnectorSession) connectorSession).getSession();
        return metadata.listSchemaNames(session, catalogName).stream()
                .filter(schema -> !predicate.isPresent() || predicate.get().test(schemaAsFixedValues(schema)))
                .map(schema -> new QualifiedTablePrefix(catalogName, schema))
                .collect(toImmutableSet());
    }

    public Set<QualifiedTablePrefix> calculatePrefixesWithTableName(
            ConnectorSession connectorSession,
            Set<QualifiedTablePrefix> prefixes,
            TupleDomain<ColumnHandle> constraint,
            Optional<Predicate<Map<ColumnHandle, NullableValue>>> predicate)
    {
        Session session = ((FullConnectorSession) connectorSession).getSession();

        Optional<Set<String>> tables = filterString(constraint, TABLE_NAME_COLUMN_HANDLE);
        if (tables.isPresent()) {
            return prefixes.stream()
                    .flatMap(prefix -> tables.get().stream()
                            .filter(this::isLowerCase)
                            .map(table -> table.toLowerCase(ENGLISH))
                            .map(table -> new QualifiedObjectName(catalogName, prefix.getSchemaName().get(), table)))
                    .filter(objectName -> metadata.getTableHandle(session, objectName).isPresent() || metadata.getView(session, objectName).isPresent())
                    .map(QualifiedObjectName::asQualifiedTablePrefix)
                    .collect(toImmutableSet());
        }

        return prefixes.stream()
                .flatMap(prefix -> Stream.concat(
                        metadata.listTables(session, prefix).stream(),
                        metadata.listViews(session, prefix).stream()))
                .filter(objectName -> !predicate.isPresent() || predicate.get().test(asFixedValues(objectName)))
                .map(QualifiedObjectName::asQualifiedTablePrefix)
                .collect(toImmutableSet());
    }

    private <T> Optional<Set<String>> filterString(TupleDomain<T> constraint, T column)
    {
        if (constraint.isNone()) {
            return Optional.of(ImmutableSet.of());
        }

        Domain domain = constraint.getDomains().get().get(column);
        if (domain == null) {
            return Optional.empty();
        }

        if (domain.isSingleValue()) {
            return Optional.of(ImmutableSet.of(((Slice) domain.getSingleValue()).toStringUtf8()));
        }
        if (domain.getValues() instanceof EquatableValueSet) {
            Collection<Object> values = ((EquatableValueSet) domain.getValues()).getValues();
            return Optional.of(values.stream()
                    .map(Slice.class::cast)
                    .map(Slice::toStringUtf8)
                    .collect(toImmutableSet()));
        }
        return Optional.empty();
    }

    private Map<ColumnHandle, NullableValue> schemaAsFixedValues(String schema)
    {
        return ImmutableMap.of(SCHEMA_COLUMN_HANDLE, new NullableValue(createUnboundedVarcharType(), utf8Slice(schema)));
    }

    private Map<ColumnHandle, NullableValue> asFixedValues(QualifiedObjectName objectName)
    {
        return ImmutableMap.of(
                CATALOG_COLUMN_HANDLE, new NullableValue(createUnboundedVarcharType(), utf8Slice(objectName.getCatalogName())),
                SCHEMA_COLUMN_HANDLE, new NullableValue(createUnboundedVarcharType(), utf8Slice(objectName.getSchemaName())),
                TABLE_NAME_COLUMN_HANDLE, new NullableValue(createUnboundedVarcharType(), utf8Slice(objectName.getObjectName())));
    }

    @Override
    public ConnectorTableLayout getTableLayout(ConnectorSession session, ConnectorTableLayoutHandle handle)
    {
        return new ConnectorTableLayout(handle);
    }

    private boolean isLowerCase(String value)
    {
        return value.toLowerCase(ENGLISH).equals(value);
    }
}
