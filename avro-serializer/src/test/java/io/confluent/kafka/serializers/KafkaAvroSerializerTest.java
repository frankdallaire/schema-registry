/**
 * Copyright 2014 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.kafka.serializers;

import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.Test;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.LocalSchemaRegistryClient;

import java.io.IOException;

import static org.junit.Assert.*;

public class KafkaAvroSerializerTest {

  private final SchemaRegistryClient schemaRegistry;
  private final KafkaAvroSerializer avroSerializer;
  private final KafkaAvroSerializer avroSerializerWithSchemaNameIncluded;
  private final KafkaAvroEncoder avroEncoder;
  private final KafkaAvroDecoder avroDecoder;
  private final String topic;

  public KafkaAvroSerializerTest() {
    schemaRegistry = new LocalSchemaRegistryClient();
    avroSerializer = new KafkaAvroSerializer(schemaRegistry);
    avroSerializerWithSchemaNameIncluded = new KafkaAvroSerializer(schemaRegistry, true);
    avroEncoder = new KafkaAvroEncoder(schemaRegistry);
    avroDecoder = new KafkaAvroDecoder(schemaRegistry);
    topic = "test";
  }

  private IndexedRecord createAvroRecord() {
    String userSchema = "{\"namespace\": \"example.avro\", \"type\": \"record\", " +
                        "\"name\": \"User\"," +
                        "\"fields\": [{\"name\": \"name\", \"type\": \"string\"}]}";
    Schema.Parser parser = new Schema.Parser();
    Schema schema = parser.parse(userSchema);
    GenericRecord avroRecord = new GenericData.Record(schema);
    avroRecord.put("name", "testUser");
    return avroRecord;
  }

  private IndexedRecord createInvalidAvroRecord() {
    String userSchema = "{\"namespace\": \"example.avro\", \"type\": \"record\", " +
                        "\"name\": \"User\"," +
                        "\"fields\": [{\"name\": \"f1\", \"type\": \"string\"}," +
                        "{\"name\": \"f2\", \"type\": \"string\"}]}";
    Schema.Parser parser = new Schema.Parser();
    Schema schema = parser.parse(userSchema);
    GenericRecord avroRecord = new GenericData.Record(schema);
    avroRecord.put("f1", "value1");
    avroRecord.put("f1", 12);
    // intentionally miss setting a required field f2
    return avroRecord;
  }

  @Test
  public void testKafkaAvroSerializer() {
    byte[] bytes;
    Object obj;
    IndexedRecord avroRecord = createAvroRecord();
    bytes = avroSerializer.serialize(topic, avroRecord);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(avroRecord, obj);

    bytes = avroSerializer.serialize(topic, null);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(null, obj);

    bytes = avroSerializer.serialize(topic, true);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(true, obj);

    bytes = avroSerializer.serialize(topic, 123);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(123, obj);

    bytes = avroSerializer.serialize(topic, 345L);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(345l, obj);

    bytes = avroSerializer.serialize(topic, 1.23f);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(1.23f, obj);

    bytes = avroSerializer.serialize(topic, 2.34d);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(2.34, obj);

    bytes = avroSerializer.serialize(topic, "abc");
    obj = avroDecoder.fromBytes(bytes);
    assertEquals("abc", obj);

    bytes = avroSerializer.serialize(topic, "abc".getBytes());
    obj = avroDecoder.fromBytes(bytes);
    assertArrayEquals("abc".getBytes(), (byte[]) obj);
  }

  @Test
  public void testNull() {
    SchemaRegistryClient nullSchemaRegistryClient = null;
    KafkaAvroSerializer nullAvroSerializer = new KafkaAvroSerializer(nullSchemaRegistryClient);

    // null doesn't require schema registration. So serialization should succeed with a null
    // schema registry client.
    assertEquals(null, nullAvroSerializer.serialize("test", null));
  }

  @Test
  public void testKafkaAvroEncoder() {
    byte[] bytes;
    Object obj;
    IndexedRecord avroRecord = createAvroRecord();
    bytes = avroEncoder.toBytes(avroRecord);
    obj = avroDecoder.fromBytes(bytes);
    assertEquals(avroRecord, obj);
  }

  @Test
  public void testInvalidInput() {
    IndexedRecord invalidRecord = createInvalidAvroRecord();
    try {
      avroSerializer.serialize(topic, invalidRecord);
      fail("Sending invalid record should fail serializer");
    } catch (SerializationException e) {
      // this is expected
    }

    try {
      avroEncoder.toBytes(invalidRecord);
      fail("Sending invalid record should fail encoder");
    } catch (SerializationException e) {
      // this is expected
    }

    try {
      avroEncoder.toBytes("abc");
      fail("Sending data of unsupported type should fail encoder");
    } catch (SerializationException e) {
      // this is expected
    }
  }

  @Test
  public void testSchemaNameInSubjectWhenSerializing() {
    IndexedRecord avroRecord = createAvroRecord();
    byte[] bytes = avroSerializerWithSchemaNameIncluded.serialize("test", avroRecord);
    Object obj = avroDecoder.fromBytes(bytes);
    assertEquals(avroRecord, obj);

    try {
      SchemaMetadata latestSchemaMetadata = schemaRegistry.getLatestSchemaMetadata("test-User-value");
      assertNotNull(latestSchemaMetadata);
    } catch (IOException e) {
      fail("Unable to find the schema named test-User-value");
    } catch (RestClientException e) {
      fail("Unable to find the schema named test-User-value");
    }
  }
}
