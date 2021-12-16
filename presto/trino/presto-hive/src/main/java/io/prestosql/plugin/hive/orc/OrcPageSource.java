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
package io.prestosql.plugin.hive.orc;

import com.google.common.collect.ImmutableList;
import io.prestosql.memory.context.AggregatedMemoryContext;
import io.prestosql.orc.OrcCorruptionException;
import io.prestosql.orc.OrcDataSource;
import io.prestosql.orc.OrcRecordReader;
import io.prestosql.plugin.hive.FileFormatDataSourceStats;
import io.prestosql.plugin.hive.HiveColumnHandle;
import io.prestosql.spi.Page;
import io.prestosql.spi.PrestoException;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.LazyBlock;
import io.prestosql.spi.block.LazyBlockLoader;
import io.prestosql.spi.block.RunLengthEncodedBlock;
import io.prestosql.spi.connector.ConnectorPageSource;
import io.prestosql.spi.type.Type;
import io.prestosql.spi.type.TypeManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;
import static io.prestosql.orc.OrcReader.MAX_BATCH_SIZE;
import static io.prestosql.plugin.hive.HiveColumnHandle.ColumnType.REGULAR;
import static io.prestosql.plugin.hive.HiveErrorCode.HIVE_BAD_DATA;
import static io.prestosql.plugin.hive.HiveErrorCode.HIVE_CURSOR_ERROR;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class OrcPageSource
        implements ConnectorPageSource
{
    private static final int NULL_ENTRY_SIZE = 0;
    private final OrcRecordReader recordReader;
    private final OrcDataSource orcDataSource;

    private final List<String> columnNames;
    private final List<Type> types;

    private final Block[] constantBlocks;
    private final int[] hiveColumnIndexes;

    private int batchId;
    private boolean closed;

    private final AggregatedMemoryContext systemMemoryContext;

    private final FileFormatDataSourceStats stats;

    public OrcPageSource(
            OrcRecordReader recordReader,
            OrcDataSource orcDataSource,
            List<HiveColumnHandle> columns,
            TypeManager typeManager,
            AggregatedMemoryContext systemMemoryContext,
            FileFormatDataSourceStats stats)
    {
        this.recordReader = requireNonNull(recordReader, "recordReader is null");
        this.orcDataSource = requireNonNull(orcDataSource, "orcDataSource is null");

        int size = requireNonNull(columns, "columns is null").size();

        this.stats = requireNonNull(stats, "stats is null");

        this.constantBlocks = new Block[size];
        this.hiveColumnIndexes = new int[size];

        ImmutableList.Builder<String> namesBuilder = ImmutableList.builder();
        ImmutableList.Builder<Type> typesBuilder = ImmutableList.builder();
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            HiveColumnHandle column = columns.get(columnIndex);
            checkState(column.getColumnType() == REGULAR, "column type must be regular");

            String name = column.getName();
            Type type = typeManager.getType(column.getTypeSignature());

            namesBuilder.add(name);
            typesBuilder.add(type);

            hiveColumnIndexes[columnIndex] = column.getHiveColumnIndex();

            if (!recordReader.isColumnPresent(column.getHiveColumnIndex())) {
                constantBlocks[columnIndex] = RunLengthEncodedBlock.create(type, null, MAX_BATCH_SIZE);
            }
        }
        types = typesBuilder.build();
        columnNames = namesBuilder.build();

        this.systemMemoryContext = requireNonNull(systemMemoryContext, "systemMemoryContext is null");
    }

    @Override
    public long getCompletedBytes()
    {
        return orcDataSource.getReadBytes();
    }

    @Override
    public long getReadTimeNanos()
    {
        return orcDataSource.getReadTimeNanos();
    }

    @Override
    public boolean isFinished()
    {
        return closed;
    }

    @Override
    public Page getNextPage()
    {
        try {
            batchId++;
            int batchSize = recordReader.nextBatch();
            if (batchSize <= 0) {
                close();
                return null;
            }

            Block[] blocks = new Block[hiveColumnIndexes.length];
            for (int fieldId = 0; fieldId < blocks.length; fieldId++) {
                if (constantBlocks[fieldId] != null) {
                    blocks[fieldId] = constantBlocks[fieldId].getRegion(0, batchSize);
                }
                else {
                    blocks[fieldId] = new LazyBlock(batchSize, new OrcBlockLoader(hiveColumnIndexes[fieldId]));
                }
            }
            return new Page(batchSize, blocks);
        }
        catch (PrestoException e) {
            closeWithSuppression(e);
            throw e;
        }
        catch (OrcCorruptionException e) {
            closeWithSuppression(e);
            throw new PrestoException(HIVE_BAD_DATA, e);
        }
        catch (IOException | RuntimeException e) {
            closeWithSuppression(e);
            throw new PrestoException(HIVE_CURSOR_ERROR, format("Failed to read ORC file: %s", orcDataSource.getId()), e);
        }
    }

    @Override
    public void close()
    {
        // some hive input formats are broken and bad things can happen if you close them multiple times
        if (closed) {
            return;
        }
        closed = true;

        try {
            stats.addMaxCombinedBytesPerRow(recordReader.getMaxCombinedBytesPerRow());
            recordReader.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("columnNames", columnNames)
                .add("types", types)
                .toString();
    }

    @Override
    public long getSystemMemoryUsage()
    {
        return systemMemoryContext.getBytes();
    }

    protected void closeWithSuppression(Throwable throwable)
    {
        requireNonNull(throwable, "throwable is null");
        try {
            close();
        }
        catch (RuntimeException e) {
            // Self-suppression not permitted
            if (throwable != e) {
                throwable.addSuppressed(e);
            }
        }
    }

    private final class OrcBlockLoader
            implements LazyBlockLoader<LazyBlock>
    {
        private final int expectedBatchId = batchId;
        private final int columnIndex;
        private boolean loaded;

        public OrcBlockLoader(int columnIndex)
        {
            this.columnIndex = columnIndex;
        }

        @Override
        public final void load(LazyBlock lazyBlock)
        {
            if (loaded) {
                return;
            }

            checkState(batchId == expectedBatchId);

            try {
                Block block = recordReader.readBlock(columnIndex);
                lazyBlock.setBlock(block);
            }
            catch (OrcCorruptionException e) {
                throw new PrestoException(HIVE_BAD_DATA, e);
            }
            catch (IOException | RuntimeException e) {
                throw new PrestoException(HIVE_CURSOR_ERROR, format("Failed to read ORC file: %s", orcDataSource.getId()), e);
            }

            loaded = true;
        }
    }
}
