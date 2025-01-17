/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.binder.statement.dml;

import lombok.Getter;
import org.apache.shardingsphere.infra.binder.statement.CommonSQLStatementContext;
import org.apache.shardingsphere.infra.binder.type.SchemaAvailable;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.CallStatement;

import java.util.List;
import java.util.Map;

/**
 * Call statement context.
 */
@Getter
public final class CallStatementContext extends CommonSQLStatementContext<CallStatement> implements SchemaAvailable {

    private final String schemaName;


    private final Map<String, ShardingSphereMetaData> metaDataMap;

    private final List<Object> parameters;


    public CallStatementContext(Map<String, ShardingSphereMetaData> metaDataMap, List<Object> parameters, final CallStatement sqlStatement, final String schemaName) {
        super(sqlStatement);
        this.schemaName = schemaName;
        this.metaDataMap = metaDataMap;
        this.parameters= parameters;
    }
}
