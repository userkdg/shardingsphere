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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.proxy.backend.session.ConnectionSession;
import org.apache.shardingsphere.proxy.backend.text.TextProtocolBackendHandler;
import org.apache.shardingsphere.proxy.backend.text.data.impl.BroadcastDatabaseBackendHandler;
import org.apache.shardingsphere.proxy.backend.text.data.impl.UnicastDatabaseBackendHandler;
import org.apache.shardingsphere.sql.parser.sql.common.statement.tcl.*;
import org.apache.shardingsphere.sql.parser.sql.dialect.statement.mysql.tcl.MySQLSetTransactionStatement;
import org.apache.shardingsphere.transaction.core.TransactionOperationType;

/**
 * Transaction backend handler factory.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransactionBackendHandlerFactory {
    
    /**
     * New instance of backend handler.
     * 
     * @param sqlStatementContext SQL statement context
     * @param sql SQL
     * @param connectionSession connection session
     * @return backend handler
     */
    public static TextProtocolBackendHandler newInstance(final SQLStatementContext<? extends TCLStatement> sqlStatementContext, final String sql, final ConnectionSession connectionSession) {
        TCLStatement tclStatement = sqlStatementContext.getSqlStatement();
        log.info("tclStatement={}, sql={}", tclStatement, sql);
        if (tclStatement instanceof BeginTransactionStatement) {
            return new TransactionBackendHandler(tclStatement, TransactionOperationType.BEGIN, connectionSession);
        }
        if (tclStatement instanceof SetAutoCommitStatement) {
            return new TransactionAutoCommitHandler((SetAutoCommitStatement) tclStatement, connectionSession);
        }
        if (tclStatement instanceof SavepointStatement) {
            return new TransactionBackendHandler(tclStatement, TransactionOperationType.SAVEPOINT, connectionSession);
        }
        if (tclStatement instanceof ReleaseSavepointStatement) {
            return new TransactionBackendHandler(tclStatement, TransactionOperationType.RELEASE_SAVEPOINT, connectionSession);
        }
        if (tclStatement instanceof CommitStatement) {
            return new TransactionBackendHandler(tclStatement, TransactionOperationType.COMMIT, connectionSession);
        }
        if (tclStatement instanceof RollbackStatement) {
            return ((RollbackStatement) tclStatement).getSavepointName().isPresent() 
                    ? new TransactionBackendHandler(tclStatement, TransactionOperationType.ROLLBACK_TO_SAVEPOINT, connectionSession)
                    : new TransactionBackendHandler(tclStatement, TransactionOperationType.ROLLBACK, connectionSession); 
        }
        if (tclStatement instanceof XAStatement) {
            return new TransactionXAHandler(sqlStatementContext, sql, connectionSession);
        }
        if (tclStatement instanceof SetTransactionStatement){
            return new UnicastDatabaseBackendHandler(sqlStatementContext, sql, connectionSession);
        }
        log.warn("其他TCLStatement是全广播到所有schema, tclStatement={}, sql={}, from schema={}", tclStatement, sql, connectionSession.getSchemaName());
        return new BroadcastDatabaseBackendHandler(sqlStatementContext, sql, connectionSession);
    }
}
