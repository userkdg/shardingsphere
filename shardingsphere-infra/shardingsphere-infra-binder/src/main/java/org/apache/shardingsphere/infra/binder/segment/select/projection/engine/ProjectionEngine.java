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

package org.apache.shardingsphere.infra.binder.segment.select.projection.engine;

import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.binder.segment.select.projection.DerivedColumn;
import org.apache.shardingsphere.infra.binder.segment.select.projection.Projection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.AggregationDistinctProjection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.AggregationProjection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ColumnProjection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ExpressionProjection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ParameterMarkerProjection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ShorthandProjection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.SubqueryProjection;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.sql.parser.sql.common.constant.AggregationType;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.simple.ParameterMarkerExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.AggregationDistinctProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.AggregationProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.ColumnProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.ExpressionProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.ProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.ShorthandProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.SubqueryProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.table.JoinTableSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.table.SimpleTableSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.table.SubqueryTableSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.table.TableSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.SelectStatement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Projection engine.
 */
@RequiredArgsConstructor
public final class ProjectionEngine {
    
    private final ShardingSphereSchema schema;
    
    private final DatabaseType databaseType;
    
    private int aggregationAverageDerivedColumnCount;
    
    private int aggregationDistinctDerivedColumnCount;
    
    /**
     * Create projection.
     * 
     * @param table table segment
     * @param projectionSegment projection segment
     * @return projection
     */
    public Optional<Projection> createProjection(final TableSegment table, final ProjectionSegment projectionSegment) {
        if (projectionSegment instanceof ShorthandProjectionSegment) {
            final ShorthandProjection shorthandProjection = createProjection(table, (ShorthandProjectionSegment) projectionSegment);
            final ShorthandProjection actualShorthandProjection = shorthandProjection.getOwner().map(owner -> {
                List<ColumnProjection> actProjections = new LinkedList<>();
                for (Map.Entry<String, ColumnProjection> entry : shorthandProjection.getActualColumns().entrySet()) {
                    ColumnProjection p = entry.getValue();
                    if (owner.equalsIgnoreCase(p.getOwner())) {
                        actProjections.add(p);
                    }
                }
                return new ShorthandProjection(owner, actProjections);
            }).orElse(shorthandProjection);
            // 过滤掉非当前shorthand的projection
            return Optional.of(actualShorthandProjection);
        }
        if (projectionSegment instanceof ColumnProjectionSegment) {
            return Optional.of(createProjection((ColumnProjectionSegment) projectionSegment));
        }
        if (projectionSegment instanceof ExpressionProjectionSegment) {
            return Optional.of(createProjection((ExpressionProjectionSegment) projectionSegment));
        }
        if (projectionSegment instanceof AggregationDistinctProjectionSegment) {
            return Optional.of(createProjection((AggregationDistinctProjectionSegment) projectionSegment));
        }
        if (projectionSegment instanceof AggregationProjectionSegment) {
            return Optional.of(createProjection((AggregationProjectionSegment) projectionSegment));
        }
        if (projectionSegment instanceof SubqueryProjectionSegment) {
            return Optional.of(createProjection((SubqueryProjectionSegment) projectionSegment));
        }
        if (projectionSegment instanceof ParameterMarkerExpressionSegment) {
            return Optional.of(createProjection((ParameterMarkerExpressionSegment) projectionSegment));
        }
        // TODO subquery
        return Optional.empty();
    }

    private ParameterMarkerProjection createProjection(final ParameterMarkerExpressionSegment projectionSegment) {
        return new ParameterMarkerProjection(projectionSegment.getParameterMarkerIndex(), projectionSegment.getAlias().orElse(null));
    }
    
    private SubqueryProjection createProjection(final SubqueryProjectionSegment projectionSegment) {
        return new SubqueryProjection(projectionSegment.getText(), projectionSegment.getAlias().orElse(null));
    }
    
    private ShorthandProjection createProjection(final TableSegment table, final ShorthandProjectionSegment projectionSegment) {
        String owner = projectionSegment.getOwner().map(ownerSegment -> ownerSegment.getIdentifier().getValue()).orElse(null);
        Collection<ColumnProjection> columnProjections = new LinkedHashSet<>();
        columnProjections.addAll(getShorthandColumnsFromSimpleTableSegment(table, owner));
        columnProjections.addAll(getShorthandColumnsFromSubqueryTableSegment(table));
        columnProjections.addAll(getShorthandColumnsFromJoinTableSegment(table, projectionSegment));
        return new ShorthandProjection(owner, columnProjections);
    }
    
    private ColumnProjection createProjection(final ColumnProjectionSegment projectionSegment) {
        String owner = projectionSegment.getColumn().getOwner().isPresent() ? projectionSegment.getColumn().getOwner().get().getIdentifier().getValue() : null;
        return new ColumnProjection(owner, projectionSegment.getColumn().getIdentifier().getValue(), projectionSegment.getAlias().orElse(null));
    }
    
    private ExpressionProjection createProjection(final ExpressionProjectionSegment projectionSegment) {
        return new ExpressionProjection(projectionSegment.getText(), projectionSegment.getAlias().orElse(null));
    }
    
    private AggregationDistinctProjection createProjection(final AggregationDistinctProjectionSegment projectionSegment) {
        String innerExpression = projectionSegment.getInnerExpression();
        String alias = projectionSegment.getAlias().orElse(DerivedColumn.AGGREGATION_DISTINCT_DERIVED.getDerivedColumnAlias(aggregationDistinctDerivedColumnCount++));
        AggregationDistinctProjection result = new AggregationDistinctProjection(
                projectionSegment.getStartIndex(), projectionSegment.getStopIndex(), projectionSegment.getType(), innerExpression, alias, projectionSegment.getDistinctExpression(), databaseType);
        if (AggregationType.AVG == result.getType()) {
            appendAverageDistinctDerivedProjection(result);
        }
        return result;
    }
    
    private AggregationProjection createProjection(final AggregationProjectionSegment projectionSegment) {
        String innerExpression = projectionSegment.getInnerExpression();
        AggregationProjection result = new AggregationProjection(projectionSegment.getType(), innerExpression, projectionSegment.getAlias().orElse(null), databaseType);
        if (AggregationType.AVG == result.getType()) {
            appendAverageDerivedProjection(result);
            // TODO replace avg to constant, avoid calculate useless avg
        }
        return result;
    }
    
    private Collection<ColumnProjection> getShorthandColumnsFromSimpleTableSegment(final TableSegment table, final String owner) {
        if (!(table instanceof SimpleTableSegment)) {
            return Collections.emptyList();
        }
        String tableName = ((SimpleTableSegment) table).getTableName().getIdentifier().getValue();
        String tableAlias = table.getAlias().orElse(tableName);
        Collection<ColumnProjection> result = new LinkedList<>();
        if (null == owner) {
            schema.getAllColumnNames(tableName).stream().map(columnName -> new ColumnProjection(tableAlias, columnName, null)).forEach(result::add);
        } else if (owner.equalsIgnoreCase(tableAlias)) {
            schema.getAllColumnNames(tableName).stream().map(columnName -> new ColumnProjection(owner, columnName, null)).forEach(result::add);
        }
        return result;
    }
    
    private Collection<ColumnProjection> getShorthandColumnsFromSubqueryTableSegment(final TableSegment table) {
        if (!(table instanceof SubqueryTableSegment)) {
            return Collections.emptyList();
        }
        SelectStatement subSelectStatement = ((SubqueryTableSegment) table).getSubquery().getSelect();
        Collection<Projection> projections = subSelectStatement.getProjections().getProjections().stream().map(each 
            -> createProjection(subSelectStatement.getFrom(), each).orElse(null)).filter(Objects::nonNull).collect(Collectors.toList());
        //  2021/12/30 子查询的projections都是有别名就去别名，没有的就拿name 即getColumnLabel
        String tableAlias = table.getAlias().orElseThrow(() -> new ShardingSphereException("must to define subquery alias name !"));
        return getSubqueryColumnProjections(tableAlias, projections);
    }
    
    private Collection<ColumnProjection> getShorthandColumnsFromJoinTableSegment(final TableSegment table, final ProjectionSegment projectionSegment) {
        if (!(table instanceof JoinTableSegment)) {
            return Collections.emptyList();
        }
        Collection<Projection> projections = new LinkedList<>();
        createProjection(((JoinTableSegment) table).getLeft(), projectionSegment).ifPresent(projections::add);
        createProjection(((JoinTableSegment) table).getRight(), projectionSegment).ifPresent(projections::add);
        return getColumnProjections(projections);
    }
    
    private Collection<ColumnProjection> getColumnProjections(final Collection<Projection> projections) {
        Collection<ColumnProjection> result = new LinkedList<>();
        for (Projection each : projections) {
            if (each instanceof ColumnProjection) {
                result.add((ColumnProjection) each);
            }
            if (each instanceof ShorthandProjection) {
                result.addAll(((ShorthandProjection) each).getActualColumns().values());
            }
        }
        return result;
    }

    private Collection<ColumnProjection> getSubqueryColumnProjections(String tableAlias, final Collection<Projection> projections) {
        Collection<ColumnProjection> result = new LinkedList<>();
        for (Projection each : projections) {
            if (each instanceof ColumnProjection) {
                ColumnProjection colP = (ColumnProjection) each;
                ColumnProjection newColP = new ColumnProjection(tableAlias, colP.getColumnLabel(), null);
                result.add(newColP);
            }else if (each instanceof ShorthandProjection) {
                Collection<ColumnProjection> colPs = ((ShorthandProjection) each).getActualColumns().values();
                List<ColumnProjection> newColPs = colPs.stream().map(colP -> new ColumnProjection(tableAlias, colP.getColumnLabel(), null)).collect(Collectors.toList());
                result.addAll(newColPs);
            }else if (each instanceof ExpressionProjection){
                ExpressionProjection colP = (ExpressionProjection) each;
                ColumnProjection newColP = new ColumnProjection(tableAlias, colP.getColumnLabel(), colP.getAlias().orElse(null));
                result.add(newColP);
            } else {
                ColumnProjection newColP = new ColumnProjection(tableAlias, each.getColumnLabel(), each.getAlias().orElse(null));
                result.add(newColP);
            }
        }
        return result;
    }

    private void appendAverageDistinctDerivedProjection(final AggregationDistinctProjection averageDistinctProjection) {
        String innerExpression = averageDistinctProjection.getInnerExpression();
        String distinctInnerExpression = averageDistinctProjection.getDistinctInnerExpression();
        String countAlias = DerivedColumn.AVG_COUNT_ALIAS.getDerivedColumnAlias(aggregationAverageDerivedColumnCount);
        AggregationDistinctProjection countDistinctProjection = new AggregationDistinctProjection(
                0, 0, AggregationType.COUNT, innerExpression, countAlias, distinctInnerExpression, databaseType);
        String sumAlias = DerivedColumn.AVG_SUM_ALIAS.getDerivedColumnAlias(aggregationAverageDerivedColumnCount);
        AggregationDistinctProjection sumDistinctProjection = new AggregationDistinctProjection(
                0, 0, AggregationType.SUM, innerExpression, sumAlias, distinctInnerExpression, databaseType);
        averageDistinctProjection.getDerivedAggregationProjections().add(countDistinctProjection);
        averageDistinctProjection.getDerivedAggregationProjections().add(sumDistinctProjection);
        aggregationAverageDerivedColumnCount++;
    }
    
    private void appendAverageDerivedProjection(final AggregationProjection averageProjection) {
        String innerExpression = averageProjection.getInnerExpression();
        String countAlias = DerivedColumn.AVG_COUNT_ALIAS.getDerivedColumnAlias(aggregationAverageDerivedColumnCount);
        AggregationProjection countProjection = new AggregationProjection(AggregationType.COUNT, innerExpression, countAlias, databaseType);
        String sumAlias = DerivedColumn.AVG_SUM_ALIAS.getDerivedColumnAlias(aggregationAverageDerivedColumnCount);
        AggregationProjection sumProjection = new AggregationProjection(AggregationType.SUM, innerExpression, sumAlias, databaseType);
        averageProjection.getDerivedAggregationProjections().add(countProjection);
        averageProjection.getDerivedAggregationProjections().add(sumProjection);
        aggregationAverageDerivedColumnCount++;
    }
}
