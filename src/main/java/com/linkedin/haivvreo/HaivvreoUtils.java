/*
 * Copyright 2011 LinkedIn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.haivvreo;

import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

class HaivvreoUtils {
  public static final String SCHEMA_LITERAL = "schema.literal";
  public static final String SCHEMA_URL = "schema.url";

  /**
   * Determine the schema to that's been provided for Avro serde work.
   * @param properties containing a key pointing to the schema, one way or another
   * @return schema to use while serdeing the avro file
   * @throws IOException if error while trying to read the schema from another location
   * @throws HaivvreoException if unable to find a schema or pointer to it in the properties
   */
  public static Schema determineSchema(Properties properties) throws IOException, HaivvreoException {
    String schemaString = properties.getProperty(SCHEMA_LITERAL);
    if(schemaString != null)
      return Schema.parse(schemaString);

    // Try pulling directly from URL
    schemaString = properties.getProperty(SCHEMA_URL);
    if(schemaString == null)
      throw new HaivvreoException("Neither " + SCHEMA_LITERAL + " nor "
          + SCHEMA_URL + " specified, can't determine table schema");

    try {
      if(schemaString.startsWith("hdfs://"))
        return getSchemaFromHDFS(schemaString, new Configuration());
    } catch(IOException ioe) {
      throw new HaivvreoException("Unable to read schema from HDFS: " + schemaString, ioe);
    }

    return Schema.parse(new URL(schemaString).openStream());
  }

  // Protected for testing and so we can pass in a conf for testing.
  protected static Schema getSchemaFromHDFS(String schemaHDFSUrl, Configuration conf) throws IOException {
    FileSystem fs = FileSystem.get(conf);
    FSDataInputStream in = null;

    try {
      in = fs.open(new Path(schemaHDFSUrl));
      Schema s = Schema.parse(in);
      return s;
    } finally {
      if(in != null) in.close();
    }
  }

  /**
   * Determine if an Avro schema is of type Union[T, NULL].  Avro supports nullable
   * types via a union of type T and null.  This is a very common use case.
   * As such, we want to silently convert it to just T and allow the value to be null.
   *
   * @return true if type represents Union[T, Null], false otherwise
   */
  public static boolean isNullableType(Schema schema) {
    return schema.getType().equals(Schema.Type.UNION) &&
           schema.getTypes().size() == 2 &&
             (schema.getTypes().get(0).getType().equals(Schema.Type.NULL) || // [null, null] not allowed, so this check is ok.
              schema.getTypes().get(1).getType().equals(Schema.Type.NULL));
  }

  /**
   * In a nullable type, get the schema for the non-nullable type.  This method
   * does no checking that the provides Schema is nullable.
   */
  public static Schema getOtherTypeFromNullableType(Schema schema) {
    List<Schema> types = schema.getTypes();

    return types.get(0).getType().equals(Schema.Type.NULL) ? types.get(1) : types.get(0);
  }

  public static <T> T notNull(T o, String name) throws HaivvreoException {
    if(o == null) throw new HaivvreoException(name + " cannot be null");
    return o;
  }
}
