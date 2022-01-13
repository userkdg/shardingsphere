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

package org.apache.shardingsphere.proxy.backend.text.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.proxy.backend.response.header.ResponseHeader;
import org.apache.shardingsphere.proxy.backend.response.header.update.UpdateResponseHeader;
import org.apache.shardingsphere.proxy.backend.session.ConnectionSession;
import org.apache.shardingsphere.proxy.backend.text.TextProtocolBackendHandler;
import org.apache.shardingsphere.sql.parser.sql.common.statement.tcl.SetAutoCommitStatement;
import org.apache.shardingsphere.transaction.core.TransactionOperationType;

import java.sql.SQLException;

/**
 * Set autocommit handler.
 */
@RequiredArgsConstructor
@Slf4j(topic = "SS-PROXY-TEXT-PROTOCOL-AUTO-COMMIT")
public final class TransactionAutoCommitHandler implements TextProtocolBackendHandler {

    private final SetAutoCommitStatement sqlStatement;

    private final ConnectionSession connectionSession;

    @Override
    public ResponseHeader execute() throws SQLException {
        log.info("构建执行处理：set autocommit={}, connSession={}", sqlStatement.isAutoCommit() ? 1 : 0, connectionSession);
        connectionSession.setAutoCommit(sqlStatement.isAutoCommit());
        if (!sqlStatement.isAutoCommit()){
            log.debug("构建执行处理：当set autocommit=0时，触发执行Begin事务, connSession={}", connectionSession);
            ResponseHeader execute = new TransactionBackendHandler(sqlStatement, TransactionOperationType.BEGIN, connectionSession).execute();
            log.debug("构建执行处理：当set autocommit=0时，触发执行Begin事务完成, res={}, connSession={}", execute, connectionSession);
        } else {
            log.info("完成事务操作：完成设置connectionSession#autoCommit=true，无需提交到数据库执行, connSession={}", connectionSession);
        }
        return new UpdateResponseHeader(sqlStatement);
    }
}
