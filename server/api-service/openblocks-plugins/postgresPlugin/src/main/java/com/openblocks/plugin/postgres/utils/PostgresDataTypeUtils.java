/**
 * Copyright 2021 Appsmith Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 */
// copied for postgres data types
package com.openblocks.plugin.postgres.utils;

import static com.openblocks.plugin.postgres.model.DataType.BOOLEAN;
import static com.openblocks.plugin.postgres.model.DataType.DOUBLE;
import static com.openblocks.plugin.postgres.model.DataType.FLOAT;
import static com.openblocks.plugin.postgres.model.DataType.INTEGER;
import static com.openblocks.plugin.postgres.model.DataType.LONG;
import static com.openblocks.plugin.postgres.model.DataType.STRING;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.BOOL;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.DATE;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.DECIMAL;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.FLOAT8;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.INT;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.INT4;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.INT8;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.TEXT;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.TIME;
import static com.openblocks.plugin.postgres.utils.PostgresDataTypeUtils.PostgresDataType.VARCHAR;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.openblocks.plugin.postgres.model.DataType;

public class PostgresDataTypeUtils {

    /**
     * questionWithCast will match the following sample strings in a query
     * - "?"
     * - "?::text"
     * <p>
     * Capturing only the words post "::" so that the explict data type to which the parameter must be cast can be read
     * and ignoring the group "::" from getting captured by using regex "?:" which ignores the subsequent string
     */
    private static final String questionWithCast = "\\?(?:::)*([a-zA-Z]+)*";
    private static final Pattern questionWithCastPattern = Pattern.compile(questionWithCast);

    public static PostgresDataType dataType = new PostgresDataType();

    public static class PostgresDataType {
        /**
         * Declare all the explicitly castable postgresql types below. These would be automatically added to the
         * dataTypes set automatically.
         * <p>
         * !!! WARNING !!!
         * When adding a new data type to support for explicit casting, please ensure to add an entry in the Map
         * dataTypeMapper which maps the postgres data types to  data types.
         */
        public static final String INT8 = "int8";
        public static final String INT4 = "int4";
        public static final String DECIMAL = "decimal";
        public static final String VARCHAR = "varchar";
        public static final String BOOL = "bool";
        public static final String DATE = "date";
        public static final String TIME = "time";
        public static final String FLOAT8 = "float8";
        public static final String TEXT = "text";
        public static final String INT = "int";

        public Set dataTypes = null;

        public Set getDataTypes() {
            // if data types hasn't been initialized, read and set all the supported data types for postgres
            if (dataTypes != null && !dataTypes.isEmpty()) {
                return dataTypes;
            }

            dataTypes = new HashSet<>();

            Field[] fields = this.getClass().getDeclaredFields();

            for (Field field : fields) {
                if (field.getType().equals(String.class)) { // if it is a String field
                    try {
                        dataTypes.add(field.get(dataType));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        // We weren't able to read the value of the field. Ignore this field and continue
                        // Still print the stack trace for posterity.
                        e.printStackTrace();
                    }
                }
            }
            // We are assured that data types has been set.
            return dataTypes;
        }
    }

    public static Map<String, DataType> dataTypeMapper;

    private static Map<String, DataType> getDataTypeMapper() {
        if (dataTypeMapper == null) {
            dataTypeMapper = new HashMap<>();
            dataTypeMapper.put(INT8, LONG);
            dataTypeMapper.put(INT4, INTEGER);
            dataTypeMapper.put(DECIMAL, FLOAT);
            dataTypeMapper.put(VARCHAR, STRING);
            dataTypeMapper.put(BOOL, BOOLEAN);
            dataTypeMapper.put(DATE, DataType.DATE);
            dataTypeMapper.put(TIME, DataType.TIME);
            dataTypeMapper.put(FLOAT8, DOUBLE);
            dataTypeMapper.put(TEXT, STRING);
            dataTypeMapper.put(INT, INTEGER);
        }

        return dataTypeMapper;
    }

    public static List<DataType> extractExplicitCasting(String query) {
        Matcher matcher = questionWithCastPattern.matcher(query);
        List<DataType> inputDataTypes = new ArrayList<>();

        while (matcher.find()) {
            String prospectiveDataType = matcher.group(1);

            if (prospectiveDataType != null) {
                String dataTypeFromInput = prospectiveDataType.trim().toLowerCase();
                if (dataType.getDataTypes().contains(dataTypeFromInput)) {
                    DataType commonDataType = getDataTypeMapper().get(dataTypeFromInput);
                    inputDataTypes.add(commonDataType);
                    continue;
                }
            }
            // Either no external casting exists or unsupported data type is being used. Do not use external casting for this
            // and instead default to implicit type casting (default behaviour) by setting the entry to null.
            inputDataTypes.add(null);
        }

        return inputDataTypes;
    }

    public static String toPostgresqlPrimitiveTypeName(DataType type) {
        return switch (type) {
            case LONG -> PostgresDataType.INT8;
            case INTEGER -> PostgresDataType.INT4;
            case FLOAT -> PostgresDataType.DECIMAL;
            case STRING -> PostgresDataType.VARCHAR;
            case BOOLEAN -> PostgresDataType.BOOL;
            case DATE -> PostgresDataType.DATE;
            case TIME -> PostgresDataType.TIME;
            case DOUBLE -> PostgresDataType.FLOAT8;
            case ARRAY -> throw new IllegalArgumentException("Array of Array datatype is not supported.");
            default -> throw new IllegalArgumentException("Unable to map the computed data type to primitive Postgresql type");
        };
    }

    public static DataType parseObjectDataType(Object input) {

        if (input == null || input.equals("null")) {
            return DataType.NULL;
        }

        if (input instanceof String) {
            return DataType.STRING;
        }

        if (input instanceof Collection<?>) {
            // In case of no values in the array, set this as null. Otherwise, plugins like postgres and ms-sql
            // would break while creating a SQL array.
            return DataType.ARRAY;
        }

        if (input instanceof Map<?, ?>) {
            return DataType.JSON_OBJECT;
        }

        if (input instanceof Integer) {
            return DataType.INTEGER;
        }

        if (input instanceof Long) {
            return DataType.LONG;
        }

        if (input instanceof Float) {
            return DataType.FLOAT;
        }

        if (input instanceof Double) {
            return DataType.DOUBLE;
        }

        if (input instanceof Boolean) {
            return DataType.BOOLEAN;
        }

        if (input instanceof LocalDateTime) {
            return DataType.TIMESTAMP;
        }

        if (input instanceof LocalDate) {
            return DataType.DATE;
        }
        if (input instanceof LocalTime) {
            return DataType.TIME;
        }

        return DataType.STRING;
    }
}