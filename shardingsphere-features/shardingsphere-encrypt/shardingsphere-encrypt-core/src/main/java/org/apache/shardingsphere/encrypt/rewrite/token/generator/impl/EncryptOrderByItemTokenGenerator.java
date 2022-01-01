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
import org.apache.shardingsphere.infra.binder.segment.select.orderby.OrderByItem;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ColumnProjection;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.aware.SchemaMetaDataAware;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.SubstitutableColumnNameToken;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.BinaryOperationExpression;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.ExistsSubqueryExpression;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.InExpression;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.subquery.SubqueryExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.subquery.SubquerySegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.order.item.ColumnOrderByItemSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.order.item.OrderByItemSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.predicate.WhereSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.SelectStatement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Order by item token generator for encrypt.
 */
@Setter
public final class EncryptOrderByItemTokenGenerator extends BaseEncryptSQLTokenGenerator implements CollectionSQLTokenGenerator, SchemaMetaDataAware {
    
    private ShardingSphereSchema schema;
    
    @SuppressWarnings("rawtypes")
    @Override
    protected boolean isGenerateSQLTokenForEncrypt(final SQLStatementContext sqlStatementContext) {
        return sqlStatementContext instanceof SelectStatementContext && getColumnSegments(sqlStatementContext).size() > 0
                && !((SelectStatementContext) sqlStatementContext).getAllTables().isEmpty();
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public Collection<SubstitutableColumnNameToken> generateSQLTokens(final SQLStatementContext sqlStatementContext) {
        Collection<SubstitutableColumnNameToken> result = new LinkedHashSet<>();
        for (OrderByItemSegment each : getColumnSegments(sqlStatementContext)) {
            if (each instanceof ColumnOrderByItemSegment) {
                ColumnSegment columnSegment = ((ColumnOrderByItemSegment) each).getColumn();
                Map<String, String> columnTableNames = sqlStatementContext.getTablesContext().findTableName(Collections.singletonList(buildColumnProjection(columnSegment)), schema);
                result.addAll(generateSQLTokensWithColumnSegments(Collections.singletonList(columnSegment), columnTableNames));
            }
        }
        return result;
    }

    private Collection<SubstitutableColumnNameToken> generateSQLTokensWithColumnSegments(final Collection<ColumnSegment> columnSegments, final Map<String, String> columnTableNames) {
        Collection<SubstitutableColumnNameToken> result = new LinkedList<>();
        for (ColumnSegment column : columnSegments) {
            Optional<String> tableName = findTableName(columnTableNames, buildColumnProjection(column));
            Optional<EncryptTable> encryptTable = tableName.flatMap(optional -> getEncryptRule().findEncryptTable(optional));
            if (!encryptTable.isPresent() || !encryptTable.get().findEncryptorName(column.getIdentifier().getValue()).isPresent()) {
                continue;
            }
            int startIndex = column.getOwner().isPresent() ? column.getOwner().get().getStopIndex() + 2 : column.getStartIndex();
            int stopIndex = column.getStopIndex();
            boolean queryWithCipherColumn = getEncryptRule().isQueryWithCipherColumn(tableName.orElse(""));
            if (!queryWithCipherColumn) {
                Optional<String> plainColumn = encryptTable.get().findPlainColumn(column.getIdentifier().getValue());
                if (plainColumn.isPresent()) {
                    result.add(new SubstitutableColumnNameToken(startIndex, stopIndex, getColumnProjections(plainColumn.get())));
                    continue;
                }
            }
            Optional<String> assistedQueryColumn = encryptTable.get().findAssistedQueryColumn(column.getIdentifier().getValue());
            SubstitutableColumnNameToken encryptColumnNameToken = assistedQueryColumn.map(columnName
                -> new SubstitutableColumnNameToken(startIndex, stopIndex, getColumnProjections(columnName))).orElseGet(()
                    -> new SubstitutableColumnNameToken(startIndex, stopIndex, getColumnProjections(encryptTable.get().getCipherColumn(column.getIdentifier().getValue()))));
            result.add(encryptColumnNameToken);
        }
        return result;
    }
    
    private Collection<OrderByItemSegment> getColumnSegments(final SQLStatementContext<?> sqlStatementContext) {
        Collection<OrderByItemSegment> result = new LinkedList<>();
        if (sqlStatementContext instanceof SelectStatementContext) {
            List<SelectStatementContext> contexts = new LinkedList<>();
            contexts.add((SelectStatementContext) sqlStatementContext);
            contexts.addAll(((SelectStatementContext) sqlStatementContext).getSubqueryContexts().values());
            Collection<OrderByItem> orderByItemList = new LinkedList<>();
            for (SelectStatementContext selectStatementContext : contexts) {
                if (!selectStatementContext.getOrderByContext().isGenerated()) {
                    orderByItemList.addAll(selectStatementContext.getOrderByContext().getItems());
                }
                orderByItemList.addAll(selectStatementContext.getGroupByContext().getItems());
            }
            result.addAll(orderByItemList.stream().map(each -> each.getSegment()).collect(Collectors.toList()));
            // 找复杂表达式查询
            contexts.forEach(selectStatementContext -> {
                selectStatementContext.getWhere()
                        .map(WhereSegment::getExpr)
                        .filter(e -> e instanceof InExpression)
                        .map(e -> (InExpression) e)
                        .filter(e -> e.getRight() instanceof SubqueryExpressionSegment)
                        .map(e -> (SubqueryExpressionSegment)e.getRight())
                        .ifPresent(e -> {
                            SubquerySegment subquery = e.getSubquery();
                            SelectStatement select = subquery.getSelect();
                            select.getOrderBy().ifPresent(o -> result.addAll(o.getOrderByItems()));
                            select.getGroupBy().ifPresent(o -> result.addAll(o.getGroupByItems()));
                        });
                selectStatementContext.getWhere()
                        .map(WhereSegment::getExpr)
                        .filter(e -> e instanceof BinaryOperationExpression)
                        .map(e -> (BinaryOperationExpression) e)
                        .filter(e -> e.getRight() instanceof SubqueryExpressionSegment)
                        .map(e -> (SubqueryExpressionSegment)e.getRight())
                        .ifPresent(e -> {
                            SubquerySegment subquery = e.getSubquery();
                            SelectStatement select = subquery.getSelect();
                            select.getOrderBy().ifPresent(o -> result.addAll(o.getOrderByItems()));
                            select.getGroupBy().ifPresent(o -> result.addAll(o.getGroupByItems()));
                        });
                selectStatementContext.getWhere()
                        .map(WhereSegment::getExpr)
                        .filter(e -> e instanceof ExistsSubqueryExpression)
                        .map(e -> (ExistsSubqueryExpression) e)
                        .ifPresent(e -> {
                            SubquerySegment subquery = e.getSubquery();
                            SelectStatement select = subquery.getSelect();
                            select.getOrderBy().ifPresent(o -> result.addAll(o.getOrderByItems()));
                            select.getGroupBy().ifPresent(o -> result.addAll(o.getGroupByItems()));
                        });
            });
        }
        return result;
    }
    
    private ColumnProjection buildColumnProjection(final ColumnSegment segment) {
        String owner = segment.getOwner().map(optional -> optional.getIdentifier().getValue()).orElse(null);
        return new ColumnProjection(owner, segment.getIdentifier().getValue(), null);
    }
    
    private Optional<String> findTableName(final Map<String, String> columnTableNames, final ColumnProjection column) {
        return Optional.ofNullable(columnTableNames.get(column.getExpression()));
    }
    
    private Collection<ColumnProjection> getColumnProjections(final String columnName) {
        return Collections.singletonList(new ColumnProjection(null, columnName, null));
    }
}
