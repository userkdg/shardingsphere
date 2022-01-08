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

package org.apache.shardingsphere.proxy.frontend.command;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.db.protocol.CommonConstants;
import org.apache.shardingsphere.db.protocol.mysql.packet.command.query.text.query.MySQLComQueryPacket;
import org.apache.shardingsphere.db.protocol.packet.CommandPacket;
import org.apache.shardingsphere.db.protocol.packet.CommandPacketType;
import org.apache.shardingsphere.db.protocol.packet.DatabasePacket;
import org.apache.shardingsphere.db.protocol.payload.PacketPayload;
import org.apache.shardingsphere.distsql.parser.statement.DistSQLStatement;
import org.apache.shardingsphere.infra.database.type.dialect.MySQLDatabaseType;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.infra.parser.ShardingSphereSQLParserEngine;
import org.apache.shardingsphere.parser.rule.SQLParserRule;
import org.apache.shardingsphere.proxy.backend.communication.SQLStatementSchemaHolder;
import org.apache.shardingsphere.proxy.backend.context.ProxyContext;
import org.apache.shardingsphere.proxy.backend.exception.BackendConnectionException;
import org.apache.shardingsphere.proxy.backend.session.ConnectionSession;
import org.apache.shardingsphere.proxy.frontend.command.executor.CommandExecutor;
import org.apache.shardingsphere.proxy.frontend.command.executor.QueryCommandExecutor;
import org.apache.shardingsphere.proxy.frontend.exception.ExpectedExceptions;
import org.apache.shardingsphere.proxy.frontend.spi.DatabaseProtocolFrontendEngine;
import org.apache.shardingsphere.sql.parser.sql.common.util.SQLUtil;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Command executor task.
 */
@RequiredArgsConstructor
@Slf4j
public final class CommandExecutorTask implements Runnable {
    
    private final DatabaseProtocolFrontendEngine databaseProtocolFrontendEngine;
    
    private final ConnectionSession connectionSession;
    
    private final ChannelHandlerContext context;
    
    private final Object message;
    
    /**
     * To make sure SkyWalking will be available at the next release of ShardingSphere,
     * a new plugin should be provided to SkyWalking project if this API changed.
     *
     * @see <a href="https://github.com/apache/skywalking/blob/master/docs/en/guides/Java-Plugin-Development-Guide.md#user-content-plugin-development-guide">Plugin Development Guide</a>
     */
    @Override
    public void run() {
        boolean isNeedFlush = false;
        try (PacketPayload payload = databaseProtocolFrontendEngine.getCodecEngine().createPacketPayload((ByteBuf) message, context.channel().attr(CommonConstants.CHARSET_ATTRIBUTE_KEY).get())) {
            connectionSession.getBackendConnection().prepareForTaskExecution();
            isNeedFlush = executeCommand(context, payload);
            // CHECKSTYLE:OFF
        } catch (final Exception ex) {
            // CHECKSTYLE:ON
            processException(ex);
        } finally {
            // TODO optimize SQLStatementSchemaHolder
            SQLStatementSchemaHolder.remove();
            Collection<SQLException> exceptions = Collections.emptyList(); 
            try {
                connectionSession.getBackendConnection().closeExecutionResources();
            } catch (final BackendConnectionException ex) {
                exceptions = ex.getExceptions().stream().filter(SQLException.class::isInstance).map(SQLException.class::cast).collect(Collectors.toList());
            }
            if (isNeedFlush) {
                context.flush();
            }
            processClosedExceptions(exceptions);
        }
    }
    
    private boolean executeCommand(final ChannelHandlerContext context, final PacketPayload payload) throws SQLException {
        CommandExecuteEngine commandExecuteEngine = databaseProtocolFrontendEngine.getCommandExecuteEngine();
        CommandPacketType type = commandExecuteEngine.getCommandPacketType(payload);
        CommandPacket commandPacket = commandExecuteEngine.getCommandPacket(payload, type, connectionSession);
        List<CommandPacket> commandPackets = handlerIfMultiSqlPacket(commandPacket);
        if (commandPackets.isEmpty()) {
            commandPackets.add(commandPacket);
        }
        boolean needFlush = false;
        for (CommandPacket packet : commandPackets) {
            boolean flag = singleCommandExecutor(context, commandExecuteEngine, type, packet);
            if (!needFlush && flag){
                needFlush = true;
            }
        }
        return needFlush;
    }

    private List<CommandPacket> handlerIfMultiSqlPacket(CommandPacket commandPacket) {
        List<CommandPacket> commandPackets = new ArrayList<>();
        if (commandPacket instanceof MySQLComQueryPacket) {
            MySQLComQueryPacket packet = (MySQLComQueryPacket) commandPacket;
            final String trimSQL = SQLUtil.trimComment(packet.getSql());
            try {
                Optional<SQLParserRule> sqlParserRule = ProxyContext.getInstance().getContextManager().getMetaDataContexts().getGlobalRuleMetaData().findSingleRule(SQLParserRule.class);
                org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement shardingStatement =
                        new ShardingSphereSQLParserEngine(new MySQLDatabaseType().getName(), sqlParserRule.orElse(null))
                                .parse(trimSQL, false);
                if (shardingStatement instanceof DistSQLStatement) {
                    return commandPackets;
                }
            } catch (Exception e) {
                log.error("sharding parser error ,sql={}", trimSQL, e);
            }
            List<SQLStatement> sqlStatements = SQLUtils.parseStatements(trimSQL, DbType.mysql, true);
            // 针对多sql的情况做加工
            if (sqlStatements.size() > 1000) {
                ShardingSphereException cause = new ShardingSphereException("multi sql on one packet ,expect max size %d, actual size %d", 1000, sqlStatements.size());
                log.error("Exception occur: ", cause);
                throw cause;
            }
            Predicate<SQLStatement> crudPredicate = sqlStatement ->
                    trimSQL.toLowerCase().contains("information_schema.engines") ||
                    sqlStatement instanceof SQLDeleteStatement ||
                    sqlStatement instanceof SQLUpdateStatement ||
                    sqlStatement instanceof SQLSelectStatement ||
                    sqlStatement instanceof SQLInsertStatement;
            boolean allCrudStats = sqlStatements.stream().allMatch(crudPredicate);
            if (allCrudStats && sqlStatements.size() > 1) {
                for (SQLStatement sqlStatement : sqlStatements) {
                    String sql = SQLUtils.toSQLString(sqlStatement, DbType.mysql);
                    commandPackets.add(new MySQLComQueryPacket(sql));
                }
            }
        }
        return commandPackets;
    }

    private boolean singleCommandExecutor(ChannelHandlerContext context, CommandExecuteEngine commandExecuteEngine, CommandPacketType type, CommandPacket commandPacket) throws SQLException {
        CommandExecutor commandExecutor = commandExecuteEngine.getCommandExecutor(type, commandPacket, connectionSession);
        try {
            Collection<DatabasePacket<?>> responsePackets = commandExecutor.execute();
            if (responsePackets.isEmpty()) {
                return false;
            }
            responsePackets.forEach(context::write);
            if (commandExecutor instanceof QueryCommandExecutor) {
                return commandExecuteEngine.writeQueryData(context, connectionSession.getBackendConnection(), (QueryCommandExecutor) commandExecutor, responsePackets.size());
            }
        } finally {
            commandExecutor.close();
        }
        return databaseProtocolFrontendEngine.getFrontendContext().isFlushForPerCommandPacket();
    }

    private void processException(final Exception cause) {
        if (!ExpectedExceptions.isExpected(cause.getClass())) {
            log.error("Exception occur: ", cause);
        }
        context.write(databaseProtocolFrontendEngine.getCommandExecuteEngine().getErrorPacket(cause, connectionSession));
        Optional<DatabasePacket<?>> databasePacket = databaseProtocolFrontendEngine.getCommandExecuteEngine().getOtherPacket(connectionSession);
        databasePacket.ifPresent(context::write);
        context.flush();
    }
    
    private void processClosedExceptions(final Collection<SQLException> exceptions) {
        if (exceptions.isEmpty()) {
            return;
        }
        SQLException ex = new SQLException("");
        for (SQLException each : exceptions) {
            ex.setNextException(each);
        }
        processException(ex);
    }
}
