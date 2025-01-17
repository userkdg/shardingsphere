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

import lombok.Setter;
import org.apache.shardingsphere.encrypt.rewrite.token.generator.BaseEncryptSQLTokenGenerator;
import org.apache.shardingsphere.encrypt.rule.EncryptTable;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ColumnProjection;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.binder.type.WhereAvailable;
import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.aware.SchemaMetaDataAware;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.SubstitutableColumnNameToken;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.ExistsSubqueryExpression;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.ExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.predicate.AndPredicate;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.predicate.WhereSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.union.UnionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.OwnerSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.SelectStatement;
import org.apache.shardingsphere.sql.parser.sql.common.util.ColumnExtractor;
import org.apache.shardingsphere.sql.parser.sql.common.util.ExpressionExtractUtil;
import org.apache.shardingsphere.sql.parser.sql.common.util.WhereExtractUtil;
import org.apache.shardingsphere.sql.parser.sql.common.value.identifier.IdentifierValue;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Predicate column token generator for encrypt.
 */
@Setter
public final class EncryptPredicateColumnTokenGenerator extends BaseEncryptSQLTokenGenerator implements CollectionSQLTokenGenerator, SchemaMetaDataAware {
    
    private ShardingSphereSchema schema;
    
    @SuppressWarnings("rawtypes")
    @Override
    protected boolean isGenerateSQLTokenForEncrypt(final SQLStatementContext sqlStatementContext) {
        boolean containsJoinQuery = sqlStatementContext instanceof SelectStatementContext && ((SelectStatementContext) sqlStatementContext).isContainsJoinQuery();
        boolean containsSubquery = sqlStatementContext instanceof SelectStatementContext && ((SelectStatementContext) sqlStatementContext).isContainsSubquery();
        boolean hadWhereSegement = sqlStatementContext instanceof WhereAvailable && ((WhereAvailable) sqlStatementContext).getWhere().isPresent();
        return containsJoinQuery || containsSubquery || hadWhereSegement;
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public Collection<SubstitutableColumnNameToken> generateSQLTokens(final SQLStatementContext sqlStatementContext) {
        Collection<SubstitutableColumnNameToken> result = new LinkedHashSet<>();
        // 先拿到所有context
        List<SQLStatementContext<?>> statementContexts = getAllSqlStatementContexts(sqlStatementContext);
        List<WhereSegment> whereSegments = statementContexts.stream().flatMap(c -> getWhereSegments(c).stream()).collect(Collectors.toList());
        for (WhereSegment each : whereSegments) {
            Collection<AndPredicate> andPredicates = ExpressionExtractUtil.getAndPredicates(each.getExpr());
            Map<String, String> columnTableNames = getColumnTableNames(sqlStatementContext, andPredicates);
            for (AndPredicate predicate : andPredicates) {
                result.addAll(generateSQLTokens(predicate.getPredicates(), columnTableNames));
            }
        }
        return result;
    }

    private List<SQLStatementContext<?>> getAllSqlStatementContexts(SQLStatementContext<?> sqlStatementContext) {
        List<SQLStatementContext<?>> sqlStatementContexts = new ArrayList<>();
        sqlStatementContexts.add(sqlStatementContext);
        if (sqlStatementContext instanceof SelectStatementContext) {
            SelectStatementContext selectStatementContext = (SelectStatementContext) sqlStatementContext;
            Collection<SelectStatementContext> subContexts = (selectStatementContext).getSubqueryContexts().values();
            sqlStatementContexts.addAll(subContexts);
            sqlStatementContexts.addAll(((SelectStatementContext) sqlStatementContext).getUnionContexts().values());
        }
        return sqlStatementContexts;
    }

    private Collection<SubstitutableColumnNameToken> generateSQLTokens(final Collection<ExpressionSegment> predicates, final Map<String, String> columnTableNames) {
        Collection<SubstitutableColumnNameToken> result = new LinkedList<>();
        for (ExpressionSegment each : predicates) {
            for (ColumnSegment column : ColumnExtractor.extract(each)) {
                Optional<String> tableName = findTableName(columnTableNames, buildColumnProjection(column));
                Optional<EncryptTable> encryptTable = tableName.flatMap(optional -> getEncryptRule().findEncryptTable(optional));
                if (!encryptTable.isPresent() || !encryptTable.get().findEncryptorName(column.getIdentifier().getValue()).isPresent()) {
                    continue;
                }
                int startIndex = column.getStartIndex();
//                int startIndex = column.getOwner().isPresent() ? column.getOwner().get().getStopIndex() : column.getStartIndex();
                int stopIndex = column.getStopIndex();
                boolean queryWithCipherColumn = getEncryptRule().isQueryWithCipherColumn(tableName.orElse(""));
                String owner = column.getOwner().map(OwnerSegment::getIdentifier).map(IdentifierValue::getValue).orElse(null);
                if (!queryWithCipherColumn) {
                    Optional<String> plainColumn = encryptTable.get().findPlainColumn(column.getIdentifier().getValue());
                    if (plainColumn.isPresent()) {
                        result.add(new SubstitutableColumnNameToken(startIndex, stopIndex, getColumnProjections(owner,plainColumn.get(),null)));
                        continue;
                    }
                }
                Optional<String> assistedQueryColumn = encryptTable.get().findAssistedQueryColumn(column.getIdentifier().getValue());
                SubstitutableColumnNameToken encryptColumnNameToken = assistedQueryColumn.map(columnName
                    -> new SubstitutableColumnNameToken(startIndex, stopIndex, getColumnProjections(owner,columnName, null))).orElseGet(()
                        -> new SubstitutableColumnNameToken(startIndex, stopIndex, getColumnProjections(owner, encryptTable.get().getCipherColumn(column.getIdentifier().getValue()), null)));
                result.add(encryptColumnNameToken);
            }
        }
        return result;
    }
    
    private Collection<WhereSegment> getWhereSegments(final SQLStatementContext<?> sqlStatementContext) {
        Collection<WhereSegment> result = new LinkedList<>();
        if (sqlStatementContext instanceof SelectStatementContext) {
            result.addAll(WhereExtractUtil.getSubqueryWhereSegments((SelectStatement) sqlStatementContext.getSqlStatement()));
            result.addAll(WhereExtractUtil.getJoinWhereSegments((SelectStatement) sqlStatementContext.getSqlStatement()));
        }
        if (sqlStatementContext instanceof WhereAvailable) {
            ((WhereAvailable) sqlStatementContext).getWhere().filter(w -> !(w.getExpr() instanceof ExistsSubqueryExpression)).ifPresent(result::add);
        }
        return result;
    }
    
    private Map<String, String> getColumnTableNames(final SQLStatementContext<?> sqlStatementContext, final Collection<AndPredicate> andPredicates) {
        Collection<ColumnProjection> columns = andPredicates.stream().flatMap(each -> each.getPredicates().stream())
                .flatMap(each -> ColumnExtractor.extract(each).stream()).map(this::buildColumnProjection).collect(Collectors.toList());
        return sqlStatementContext.getTablesContext().findTableName(columns, schema);
    }
    
    private ColumnProjection buildColumnProjection(final ColumnSegment segment) {
        String owner = segment.getOwner().map(optional -> optional.getIdentifier().getValue()).orElse(null);
        return new ColumnProjection(owner, segment.getIdentifier().getValue(), null);
    }
    
    private Optional<String> findTableName(final Map<String, String> columnTableNames, final ColumnProjection column) {
        return Optional.ofNullable(columnTableNames.get(column.getExpression()));
    }
    
    private Collection<ColumnProjection> getColumnProjections(String owner, String columnName, final String alias) {
        return Collections.singletonList(new ColumnProjection(owner, columnName, alias));
    }
}
