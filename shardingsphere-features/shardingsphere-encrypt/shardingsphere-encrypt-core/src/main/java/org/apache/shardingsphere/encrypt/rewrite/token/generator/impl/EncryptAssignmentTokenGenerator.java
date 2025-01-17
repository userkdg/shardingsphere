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

package org.apache.shardingsphere.encrypt.rewrite.token.generator.impl;

import com.google.common.base.Preconditions;
import org.apache.shardingsphere.encrypt.rewrite.token.generator.BaseEncryptSQLTokenGenerator;
import org.apache.shardingsphere.encrypt.rewrite.token.pojo.EncryptAssignmentToken;
import org.apache.shardingsphere.encrypt.rewrite.token.pojo.EncryptLiteralAssignmentToken;
import org.apache.shardingsphere.encrypt.rewrite.token.pojo.EncryptParameterAssignmentToken;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.InsertStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.UpdateBatchStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.UpdateStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.util.DMLStatementContextHelper;
import org.apache.shardingsphere.infra.binder.type.TableAvailable;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.SQLToken;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.SubstitutableColumnNameToken;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.assignment.AssignmentSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.assignment.SetAssignmentSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.BinaryOperationExpression;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.simple.LiteralExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.simple.ParameterMarkerExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.subquery.SubqueryExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.predicate.WhereSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.InsertStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.UpdateBatchStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.UpdateStatement;
import org.apache.shardingsphere.sql.parser.sql.dialect.handler.dml.InsertStatementHandler;

import java.util.*;

/**
 * Assignment generator for encrypt.
 */
public final class EncryptAssignmentTokenGenerator extends BaseEncryptSQLTokenGenerator implements CollectionSQLTokenGenerator {

    @Override
    protected boolean isGenerateSQLTokenForEncrypt(final SQLStatementContext sqlStatementContext) {
        return (sqlStatementContext instanceof UpdateStatementContext ||
                sqlStatementContext instanceof UpdateBatchStatementContext)
                ||
                (sqlStatementContext instanceof InsertStatementContext
                && InsertStatementHandler.getSetAssignmentSegment(((InsertStatementContext) sqlStatementContext).getSqlStatement()).isPresent());
    }

    @Override
    public Collection<SQLToken> generateSQLTokens(final SQLStatementContext sqlStatementContext) {
        Collection<SQLToken> result = new LinkedList<>();
        String tableName = ((TableAvailable) sqlStatementContext).getAllTables().iterator().next().getTableName().getIdentifier().getValue();
        String schemaName = DMLStatementContextHelper.getSchemaName(sqlStatementContext);
        if (sqlStatementContext instanceof UpdateBatchStatementContext) {
            UpdateBatchStatement updateBatchStatement = (UpdateBatchStatement) sqlStatementContext.getSqlStatement();
            // 2022/3/26 目前antlr实现了mysql
            List<UpdateStatement> updateStatements = updateBatchStatement.getUpdateStatements();
            for (UpdateStatement each : updateStatements) {
                generateSetAssignmentSQLToken(result, tableName, schemaName, each);
                UpdateStatementContext updateStatContext = new UpdateStatementContext(
                        ((UpdateBatchStatementContext) sqlStatementContext).getMetaDataMap(),
                        ((UpdateBatchStatementContext) sqlStatementContext).getParameters(), each, schemaName);
                updateContextGenerateToken(updateStatContext, result);
            }
        } else {
            generateSetAssignmentSQLToken(result, tableName, schemaName, sqlStatementContext.getSqlStatement());
        }
        if (sqlStatementContext instanceof UpdateStatementContext) {
            updateContextGenerateToken(sqlStatementContext, result);
        }
        return result;
    }

    private void generateSetAssignmentSQLToken(Collection<SQLToken> result, String tableName, String schemaName, SQLStatement sqlStatement) {
        for (AssignmentSegment each : getSetAssignmentSegment(sqlStatement).getAssignments()) {
            if (getEncryptRule().findEncryptor(schemaName, tableName, each.getColumns().get(0).getIdentifier().getValue()).isPresent()) {
                generateSQLToken(schemaName, tableName, each).ifPresent(result::add);
            }
        }
    }

    private void updateContextGenerateToken(SQLStatementContext sqlStatementContext, Collection<SQLToken> result) {
        UpdateStatementContext updateStat = (UpdateStatementContext) sqlStatementContext;
        Optional<WhereSegment> where = (updateStat).getWhere();
        Optional<SubqueryExpressionSegment> subqueryExpressionSegment = where.map(WhereSegment::getExpr)
                .filter(e -> e instanceof BinaryOperationExpression)
                .map(e -> (BinaryOperationExpression) e)
                .filter(e -> e.getRight() instanceof SubqueryExpressionSegment)
                .map(e -> (SubqueryExpressionSegment) e.getRight());
        subqueryExpressionSegment.ifPresent(s -> {
            SelectStatementContext selectStatementContext = new SelectStatementContext(updateStat.getMetaDataMap(), updateStat.getParameters(),
                    s.getSubquery().getSelect(), updateStat.getSchemaName());

            EncryptProjectionTokenGenerator generator = new EncryptProjectionTokenGenerator();
            generator.setEncryptRule(getEncryptRule());
            if (generator.isGenerateSQLTokenForEncrypt(selectStatementContext)){
                Collection<SubstitutableColumnNameToken> projectionTokens = generator.generateSQLTokens(selectStatementContext);
                result.addAll(projectionTokens);
            }
            EncryptPredicateColumnTokenGenerator tokenGenerator = new EncryptPredicateColumnTokenGenerator();
            tokenGenerator.setEncryptRule(getEncryptRule());
            if (tokenGenerator.isGenerateSQLTokenForEncrypt(sqlStatementContext)) {
                Collection<SubstitutableColumnNameToken> whereTokens = tokenGenerator.generateSQLTokens(selectStatementContext);
                result.addAll(whereTokens);
            }
            EncryptPredicateRightValueTokenGenerator predicateTokenGen = new EncryptPredicateRightValueTokenGenerator();
            predicateTokenGen.setEncryptRule(getEncryptRule());
            if (predicateTokenGen.isGenerateSQLTokenForEncrypt(selectStatementContext)){
                Collection<SQLToken> sqlTokens = predicateTokenGen.generateSQLTokens(selectStatementContext);
                result.addAll(sqlTokens);
            }
        });
    }

    private SetAssignmentSegment getSetAssignmentSegment(final SQLStatement sqlStatement) {
        if (sqlStatement instanceof InsertStatement) {
            Optional<SetAssignmentSegment> result = InsertStatementHandler.getSetAssignmentSegment((InsertStatement) sqlStatement);
            Preconditions.checkState(result.isPresent());
            return result.get();
        }
        return ((UpdateStatement) sqlStatement).getSetAssignment();
    }

    private Optional<EncryptAssignmentToken> generateSQLToken(final String schemaName, final String tableName, final AssignmentSegment assignmentSegment) {
        if (assignmentSegment.getValue() instanceof ParameterMarkerExpressionSegment) {
            return Optional.of(generateParameterSQLToken(tableName, assignmentSegment));
        }
        if (assignmentSegment.getValue() instanceof LiteralExpressionSegment) {
            return Optional.of(generateLiteralSQLToken(schemaName, tableName, assignmentSegment));
        }
        return Optional.empty();
    }

    private EncryptAssignmentToken generateParameterSQLToken(final String tableName, final AssignmentSegment assignmentSegment) {
        EncryptParameterAssignmentToken result = new EncryptParameterAssignmentToken(assignmentSegment.getColumns().get(0).getStartIndex(), assignmentSegment.getStopIndex());
        String columnName = assignmentSegment.getColumns().get(0).getIdentifier().getValue();
        addCipherColumn(tableName, columnName, result);
        addAssistedQueryColumn(tableName, columnName, result);
        addPlainColumn(tableName, columnName, result);
        return result;
    }

    private void addCipherColumn(final String tableName, final String columnName, final EncryptParameterAssignmentToken token) {
        token.addColumnName(getEncryptRule().getCipherColumn(tableName, columnName));
    }

    private void addAssistedQueryColumn(final String tableName, final String columnName, final EncryptParameterAssignmentToken token) {
        getEncryptRule().findAssistedQueryColumn(tableName, columnName).ifPresent(token::addColumnName);
    }

    private void addPlainColumn(final String tableName, final String columnName, final EncryptParameterAssignmentToken token) {
        getEncryptRule().findPlainColumn(tableName, columnName).ifPresent(token::addColumnName);
    }

    private EncryptAssignmentToken generateLiteralSQLToken(final String schemaName, final String tableName, final AssignmentSegment assignmentSegment) {
        EncryptLiteralAssignmentToken result = new EncryptLiteralAssignmentToken(assignmentSegment.getColumns().get(0).getStartIndex(), assignmentSegment.getStopIndex());
        addCipherAssignment(schemaName, tableName, assignmentSegment, result);
        addAssistedQueryAssignment(schemaName, tableName, assignmentSegment, result);
        addPlainAssignment(tableName, assignmentSegment, result);
        return result;
    }

    private void addCipherAssignment(final String schemaName, final String tableName, final AssignmentSegment assignmentSegment, final EncryptLiteralAssignmentToken token) {
        Object originalValue = ((LiteralExpressionSegment) assignmentSegment.getValue()).getLiterals();
        Object cipherValue = getEncryptRule().getEncryptValues(schemaName, tableName, assignmentSegment.getColumns().get(0).getIdentifier().getValue(),
                Collections.singletonList(originalValue)).iterator().next();
        token.addAssignment(getEncryptRule().getCipherColumn(tableName, assignmentSegment.getColumns().get(0).getIdentifier().getValue()), cipherValue);
    }

    private void addAssistedQueryAssignment(final String schemaName, final String tableName, final AssignmentSegment assignmentSegment, final EncryptLiteralAssignmentToken token) {
        Object originalValue = ((LiteralExpressionSegment) assignmentSegment.getValue()).getLiterals();
        Optional<String> assistedQueryColumn = getEncryptRule().findAssistedQueryColumn(tableName, assignmentSegment.getColumns().get(0).getIdentifier().getValue());
        assistedQueryColumn.ifPresent(s -> {
            Object assistedQueryValue = getEncryptRule().getEncryptAssistedQueryValues(schemaName,
                    tableName, assignmentSegment.getColumns().get(0).getIdentifier().getValue(), Collections.singletonList(originalValue)).iterator().next();
            token.addAssignment(s, assistedQueryValue);
        });
    }

    private void addPlainAssignment(final String tableName, final AssignmentSegment assignmentSegment, final EncryptLiteralAssignmentToken token) {
        Object originalValue = ((LiteralExpressionSegment) assignmentSegment.getValue()).getLiterals();
        getEncryptRule().findPlainColumn(tableName, assignmentSegment.getColumns().get(0).getIdentifier().getValue()).ifPresent(plainColumn -> token.addAssignment(plainColumn, originalValue));
    }
}
