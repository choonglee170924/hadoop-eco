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
import com.google.common.collect.ImmutableList;
import io.prestosql.spi.HostAddress;
import io.prestosql.spi.connector.ConnectorSplit;

import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class ElasticsearchSplit
        implements ConnectorSplit
{
    private final String index;
    private final String type;
    private final int shard;
    private final String searchNode;
    private final int port;

    @JsonCreator
    public ElasticsearchSplit(
            @JsonProperty("index") String index,
            @JsonProperty("type") String type,
            @JsonProperty("shard") int shard,
            @JsonProperty("searchNode") String searchNode,
            @JsonProperty("port") int port)
    {
        this.index = requireNonNull(index, "index is null");
        this.type = requireNonNull(type, "index is null");
        this.searchNode = requireNonNull(searchNode, "searchNode is null");
        this.port = port;
        this.shard = shard;
    }

    @JsonProperty
    public String getIndex()
    {
        return index;
    }

    @JsonProperty
    public String getType()
    {
        return type;
    }

    @JsonProperty
    public int getShard()
    {
        return shard;
    }

    @JsonProperty
    public String getSearchNode()
    {
        return searchNode;
    }

    @JsonProperty
    public int getPort()
    {
        return port;
    }

    @Override
    public boolean isRemotelyAccessible()
    {
        return true;
    }

    @Override
    public List<HostAddress> getAddresses()
    {
        return ImmutableList.of(HostAddress.fromParts(searchNode, port));
    }

    @Override
    public Object getInfo()
    {
        return this;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .addValue(index)
                .addValue(type)
                .addValue(shard)
                .addValue(port)
                .addValue(searchNode)
                .toString();
    }
}
