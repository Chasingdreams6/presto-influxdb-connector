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

package com.facebook.presto.influxdb;

import com.facebook.presto.common.type.BigintType;
import com.facebook.presto.common.type.DoubleType;
import com.facebook.presto.common.type.TimestampType;
import com.facebook.presto.spi.ColumnMetadata;
import com.influxdb.client.BucketsApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.domain.Bucket;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class InfluxdbUtil
{
    private static final String token = "zNBXClD-3rbf82GiGNGNxx0lZsJeJ3RCc7ViONhffoKfp5tfbv1UtLGFFcw7IU9i4ebllttDWzaD3899LaRQKg==";
    private static final String org = "sjtu";
    private static final String bucket = "test";
    private static final String time_interval = "-14d";
    private static Logger logger = LoggerFactory.getLogger(InfluxdbUtil.class);
    private static InfluxDBClient influxDBClient;

    private InfluxdbUtil()
    {
    }

    public static void instance(String url)
            throws IOException
    {
        influxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org);
    }

    public static List<String> getSchemas()
    {
        //logger.debug("getSchemas");
        System.out.println("influxdbUtil-??????schemas");
        List<String> res = new ArrayList<>();
        BucketsApi bucketsApi = influxDBClient.getBucketsApi();
        for (Bucket bucket1 : bucketsApi.findBuckets()) {
            res.add(bucket1.getName());
            //System.out.println(bucket1.getName());
        }
        return res;
    }

    public static List<String> getTableNames(String bucket)
    {
        System.out.println("influxdbUtil- ????????????bucket????????????tableNames" + bucket);
        //logger.debug("getTableNames in bucket: {}", bucket);
        List<String> res = new ArrayList<>();
        QueryApi queryApi = influxDBClient.getQueryApi();
        String flux = "import  \"influxdata/influxdb/schema\"\n" + "import \"strings\"\n" + "schema.measurements(bucket: \"" + bucket + "\")\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"task\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"storage\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"service\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"query\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"qc\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"influxdb\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"http\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"go\"))\n" + "|> filter(fn : (r) => not strings.hasPrefix(v: r._value, prefix: \"boltdb\"))";
        List<FluxTable> tables = queryApi.query(flux);
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord fluxRecord : records) {
                res.add((String) fluxRecord.getValue());
                //System.out.println(fluxRecord.getValue());
            }
        }
        return res;
    }

    public static List<ColumnMetadata> getColumns(String bucket, String tableName)
    {
        System.out.println("influxdbUtil ??????bucket:" + bucket + "table:" + tableName + "????????????columnsMetadata");
        //logger.debug("getColumns in bucket: {}, table : {}", bucket, tableName);
        List<ColumnMetadata> res = new ArrayList<>();
        QueryApi queryApi = influxDBClient.getQueryApi();
        // all fields
        String flux = "import \"influxdata/influxdb/schema\"\n" + "schema.measurementFieldKeys(\n" + "    bucket : \"" + bucket + "\",\n" + "    measurement : \"" + tableName + "\",\n" + "    start:" + time_interval + ")";
        List<FluxTable> tables = queryApi.query(flux);
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord record : records) {
                res.add(new ColumnMetadata((String) record.getValue(), DoubleType.DOUBLE));
            }
        }
        // all tags
        flux = "import \"influxdata/influxdb/schema\"\n" + "schema.measurementTagKeys(\n" + "    bucket : \"" + bucket + "\",\n" + "    measurement : \"" + tableName + "\",\n" + "    start : " + time_interval + ")";
        tables = queryApi.query(flux);
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord record : records) {
                if (!((String) record.getValue()).startsWith("_")) {
                    res.add(new ColumnMetadata((String) record.getValue(), BigintType.BIGINT));
                }
            }
        }
        res.add(new ColumnMetadata("_time", TimestampType.TIMESTAMP));
        for (ColumnMetadata columnMetadata : res) {
            System.out.println(columnMetadata.getName() + ":" + columnMetadata.getType().getDisplayName());
        }
        return res;
    }

    public static Iterator<InfluxdbRow> select(String tableName)
    {
        System.out.println("influxdbUtil-???????????????" + tableName + "?????????????????????????????????");
        //logger.debug("select all rows in table: {}", tableName);
        List<InfluxdbRow> list = new ArrayList<>();
        QueryApi queryApi = influxDBClient.getQueryApi();
        String flux = "from(bucket: " + "\"" + bucket + "\"" + ")\n" + "|> range(start:" + time_interval + ")\n" + "|> filter(fn : (r) => r._measurement == " + "\"" + tableName + "\"" + ")";
        List<FluxTable> tables = queryApi.query(flux, org);
        Map<Instant, Map<String, Object>> resMap = new HashMap<>();
        for (FluxTable fluxTable : tables) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (FluxRecord fluxRecord : records) {
                Map<String, Object> curRow = resMap.get(fluxRecord.getTime());
                if (curRow == null) {
                    curRow = fluxRecord.getValues();
                    Map<String, Object> newRow = new HashMap<>();
                    for (Map.Entry<String, Object> entry : curRow.entrySet()) {
                        if (!Objects.equals(entry.getKey(), "_field") && !Objects.equals(entry.getKey(), "_value")) {
                            newRow.put(entry.getKey(), entry.getValue());
                        }
                    }
                    newRow.put(fluxRecord.getField(), fluxRecord.getValue());
                    resMap.put(fluxRecord.getTime(), newRow);
                }
                else {
                    curRow.put(fluxRecord.getField(), fluxRecord.getValue());
                }
            }
        }
        // for debug
        for (Map.Entry<Instant, Map<String, Object>> entry : resMap.entrySet()) {
            for (Map.Entry<String, Object> entry1 : entry.getValue().entrySet()) {
                System.out.println("k-v pair" + entry1.getKey() + ":" + entry1.getValue().toString());
            }
            list.add(new InfluxdbRow(entry.getValue()));
        }
        return list.iterator();
    }
}
