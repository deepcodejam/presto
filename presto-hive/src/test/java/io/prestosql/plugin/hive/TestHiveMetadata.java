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
package io.prestosql.plugin.hive;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonCodec;
import io.airlift.slice.Slices;
import io.airlift.units.Duration;
import io.prestosql.plugin.hive.authentication.NoHdfsAuthentication;
import io.prestosql.plugin.hive.metastore.CachingHiveMetastore;
import io.prestosql.plugin.hive.metastore.HiveMetastore;
import io.prestosql.plugin.hive.metastore.thrift.BridgingHiveMetastore;
import io.prestosql.plugin.hive.metastore.thrift.MetastoreLocator;
import io.prestosql.plugin.hive.metastore.thrift.TestingMetastoreLocator;
import io.prestosql.plugin.hive.metastore.thrift.ThriftHiveMetastore;
import io.prestosql.plugin.hive.metastore.thrift.ThriftHiveMetastoreConfig;
import io.prestosql.plugin.hive.security.SqlStandardAccessControlMetadata;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.connector.ConnectorTableHandle;
import io.prestosql.spi.connector.SchemaTableName;
import io.prestosql.spi.predicate.NullableValue;
import io.prestosql.spi.type.TypeSignature;
import io.prestosql.spi.type.VarcharType;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.prestosql.plugin.hive.HiveMetadata.createPredicate;
import static io.prestosql.plugin.hive.HiveTestUtils.SESSION;
import static io.prestosql.plugin.hive.HiveTestUtils.TYPE_MANAGER;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newFixedThreadPool;

public class TestHiveMetadata
{
    protected static final String TEST_SERVER_VERSION = "test_version";
    private static final HiveColumnHandle TEST_COLUMN_HANDLE = new HiveColumnHandle(
            "test",
            HiveType.HIVE_STRING,
            TypeSignature.parseTypeSignature("varchar"),
            0,
            HiveColumnHandle.ColumnType.PARTITION_KEY,
            Optional.empty());

    @Test(timeOut = 5000)
    public void testCreatePredicate()
    {
        ImmutableList.Builder<HivePartition> partitions = ImmutableList.builder();

        for (int i = 0; i < 5_000; i++) {
            partitions.add(new HivePartition(
                    new SchemaTableName("test", "test"),
                    Integer.toString(i),
                    ImmutableMap.of(TEST_COLUMN_HANDLE, NullableValue.of(VarcharType.VARCHAR, Slices.utf8Slice(Integer.toString(i))))));
        }

        createPredicate(ImmutableList.of(TEST_COLUMN_HANDLE), partitions.build());
    }

    @Test
    public void testCreateOnlyNullsPredicate()
    {
        ImmutableList.Builder<HivePartition> partitions = ImmutableList.builder();

        for (int i = 0; i < 5; i++) {
            partitions.add(new HivePartition(
                    new SchemaTableName("test", "test"),
                    Integer.toString(i),
                    ImmutableMap.of(TEST_COLUMN_HANDLE, NullableValue.asNull(VarcharType.VARCHAR))));
        }

        createPredicate(ImmutableList.of(TEST_COLUMN_HANDLE), partitions.build());
    }

    @Test
    public void testCreateMixedPredicate()
    {
        ImmutableList.Builder<HivePartition> partitions = ImmutableList.builder();

        for (int i = 0; i < 5; i++) {
            partitions.add(new HivePartition(
                    new SchemaTableName("test", "test"),
                    Integer.toString(i),
                    ImmutableMap.of(TEST_COLUMN_HANDLE, NullableValue.of(VarcharType.VARCHAR, Slices.utf8Slice(Integer.toString(i))))));
        }

        partitions.add(new HivePartition(
                new SchemaTableName("test", "test"),
                "null",
                ImmutableMap.of(TEST_COLUMN_HANDLE, NullableValue.asNull(VarcharType.VARCHAR))));

        createPredicate(ImmutableList.of(TEST_COLUMN_HANDLE), partitions.build());
    }

    @Test
    public void testGetInsertLayout()
    {
        ExecutorService executor = newCachedThreadPool(daemonThreadsNamed("hive-%s"));
        /*HiveConfig config = new HiveConfig(); //TODO: import
        HdfsConfiguration hdfsConfiguration = new HiveHdfsConfiguration(new HdfsConfigurationInitializer(config), ImmutableSet.of());
        HdfsEnvironment hdfsEnvironment = new HdfsEnvironment(hdfsConfiguration, config, new NoHdfsAuthentication()); //TODO: import
        HiveMetastore metastore = new CachingHiveMetastore(
                new BridgingHiveMetastore(new ThriftHiveMetastore(metastoreLocator, new ThriftHiveMetastoreConfig())),
                executor,
                Duration.valueOf("1m"),
                Duration.valueOf("15s"),
                10000);
        LocationService locationService = new HiveLocationService(hdfsEnvironment);
        HivePartitionManager hivePartitionManager = new HivePartitionManager(TYPE_MANAGER, config);
        JsonCodec<PartitionUpdate> partitionUpdateCodec = JsonCodec.jsonCodec(PartitionUpdate.class);
        HiveMetadataFactory metadataFactory = new HiveMetadataFactory(
                config,
                metastoreClient,
                hdfsEnvironment,
                hivePartitionManager,
                newDirectExecutorService(),
                TYPE_MANAGER,
                locationService,
                partitionUpdateCodec,
                new HiveTypeTranslator(),
                new NodeVersion("test_version"),
                SqlStandardAccessControlMetadata::new);*/
        String host = "localhost";
        int port = 9083;
        HiveConfig hiveConfig = new HiveConfig();
        MetastoreLocator metastoreLocator = new TestingMetastoreLocator(hiveConfig, host, port);
        HiveMetastore metastore = new CachingHiveMetastore(
                new BridgingHiveMetastore(new ThriftHiveMetastore(metastoreLocator, new ThriftHiveMetastoreConfig())),
                executor,
                Duration.valueOf("1m"),
                Duration.valueOf("15s"),
                10000);
        HivePartitionManager partitionManager = new HivePartitionManager(TYPE_MANAGER, hiveConfig);
        HdfsConfiguration hdfsConfiguration = new HiveHdfsConfiguration(new HdfsConfigurationInitializer(hiveConfig), ImmutableSet.of());
        HdfsEnvironment hdfsEnvironment = new HdfsEnvironment(hdfsConfiguration, hiveConfig, new NoHdfsAuthentication());
        LocationService locationService = new HiveLocationService(hdfsEnvironment);
        JsonCodec<PartitionUpdate> partitionUpdateCodec = JsonCodec.jsonCodec(PartitionUpdate.class);
        DateTimeZone timeZone = DateTimeZone.forTimeZone(TimeZone.getTimeZone(hiveConfig.getTimeZone()));
        HiveMetadataFactory metadataFactory = new HiveMetadataFactory(
                metastore,
                hdfsEnvironment,
                partitionManager,
                timeZone,
                10,
                true,
                false,
                false,
                false,
                true,
                1000,
                TYPE_MANAGER,
                locationService,
                partitionUpdateCodec,
                newFixedThreadPool(2),
                new HiveTypeTranslator(),
                TEST_SERVER_VERSION,
                SqlStandardAccessControlMetadata::new);
        HiveMetadata hiveMetadata = metadataFactory.get();
        ConnectorSession session = SESSION;
        ConnectorTableHandle tableHandle = new HiveTableHandle("test", "test", ImmutableList.of(), Optional.empty());
        hiveMetadata.getInsertLayout(session, tableHandle);

        /*ImmutableList.Builder<HivePartition> partitions = ImmutableList.builder();

        for (int i = 0; i < 5_000; i++) {
            partitions.add(new HivePartition(
                    new SchemaTableName("test", "test"),
                    Integer.toString(i),
                    ImmutableMap.of(TEST_COLUMN_HANDLE, NullableValue.of(VarcharType.VARCHAR, Slices.utf8Slice(Integer.toString(i))))));
        }

        createPredicate(ImmutableList.of(TEST_COLUMN_HANDLE), partitions.build());*/
    }
}
