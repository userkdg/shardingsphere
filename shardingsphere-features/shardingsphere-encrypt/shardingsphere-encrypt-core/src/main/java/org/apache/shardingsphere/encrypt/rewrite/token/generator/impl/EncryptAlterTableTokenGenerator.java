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

import com.google.common.base.Strings;
import org.apache.shardingsphere.encrypt.rewrite.token.generator.BaseEncryptSQLTokenGenerator;
import org.apache.shardingsphere.encrypt.rewrite.token.pojo.EncryptAlterTableToken;
import org.apache.shardingsphere.encrypt.spi.EncryptAlgorithm;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ColumnProjection;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.ddl.AlterTableStatementContext;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.SQLToken;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.Substitutable;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.RemoveToken;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.SubstitutableColumnNameToken;
import org.apache.shardingsphere.sql.parser.sql.common.segment.ddl.column.ColumnDefinitionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.ddl.column.alter.AddColumnDefinitionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.ddl.column.alter.DropColumnDefinitionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.ddl.column.alter.ModifyColumnDefinitionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.ddl.column.position.ColumnAfterPositionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.OwnerSegment;
import org.apache.shardingsphere.sql.parser.sql.common.value.identifier.IdentifierValue;

import java.util.*;

/**
 * Alter table token generator for encrypt.
 */
public final class EncryptAlterTableTokenGenerator extends BaseEncryptSQLTokenGenerator implements CollectionSQLTokenGenerator<AlterTableStatementContext> {

    @Override
    protected boolean isGenerateSQLTokenForEncrypt(final SQLStatementContext sqlStatementContext) {
        return sqlStatementContext instanceof AlterTableStatementContext;
    }

    @Override
    public Collection<SQLToken> generateSQLTokens(final AlterTableStatementContext alterTableStatementContext) {
        String tableName = alterTableStatementContext.getSqlStatement().getTable().getTableName().getIdentifier().getValue();
        Collection<SQLToken> result = new LinkedList<>(getAddColumnTokens(tableName, alterTableStatementContext.getSqlStatement().getAddColumnDefinitions()));
        result.addAll(getModifyColumnTokens(tableName, alterTableStatementContext.getSqlStatement().getModifyColumnDefinitions()));
        Collection<SQLToken> dropCollection = getDropColumnTokens(tableName, alterTableStatementContext.getSqlStatement().getDropColumnDefinitions());
        String databaseName = alterTableStatementContext.getDatabaseType().getName();
        if ("SQLServer".equals(databaseName)) {
            result.addAll(mergeDropColumnStatement(dropCollection, "", ""));
        } else if ("Oracle".equals(databaseName)) {
            result.addAll(mergeDropColumnStatement(dropCollection, "(", ")"));
        } else {
            result.addAll(dropCollection);
        }
        return result;
    }

    private Collection<SQLToken> mergeDropColumnStatement(final Collection<SQLToken> dropCollection, final String leftJoiner, final String rightJoiner) {
        Collection<SQLToken> result = new LinkedList<>();
        ArrayList<String> dropColumnList = new ArrayList<>();
        int lastStartIndex = -1;
        for (int i = 0; i < dropCollection.size(); i++) {
            SQLToken token = (SQLToken) ((List) dropCollection).get(i);
            if (token instanceof RemoveToken) {
                if (i != 0) {
                    result.add(new RemoveToken(lastStartIndex, ((RemoveToken) token).getStopIndex()));
                } else {
                    result.add(token);
                }
            } else {
                EncryptAlterTableToken encryptAlterTableToken = (EncryptAlterTableToken) token;
                dropColumnList.add(encryptAlterTableToken.getColumnName());
                if (i == dropCollection.size() - 1) {
                    result.add(new EncryptAlterTableToken(token.getStartIndex(), encryptAlterTableToken.getStopIndex(),
                            leftJoiner + String.join(",", dropColumnList) + rightJoiner, "DROP COLUMN"));
                }
            }
            lastStartIndex = ((Substitutable) token).getStartIndex();
        }
        return result;
    }

    private Collection<SQLToken> getAddColumnTokens(final String tableName, final Collection<AddColumnDefinitionSegment> columnDefinitionSegments) {
        Collection<SQLToken> result = new LinkedList<>();
        for (AddColumnDefinitionSegment each : columnDefinitionSegments) {
            result.addAll(getAddColumnTokens(tableName, each));
            each.getColumnPosition().filter(c -> c instanceof ColumnAfterPositionSegment)
                    .map(c -> (ColumnAfterPositionSegment) c)
                    .map(c -> {
                        Optional<EncryptAlgorithm> encryptor = getEncryptRule().findEncryptor(tableName, c.getColumnName().getQualifiedName());
                        String owner = c.getColumnName().getOwner().map(OwnerSegment::getIdentifier).map(IdentifierValue::getValue).orElse(null);
                        return encryptor.map(col -> {
                            String cipherColumn = getEncryptRule().getCipherColumn(tableName, c.getColumnName().getQualifiedName());
                            ColumnProjection columnProjection = new ColumnProjection(owner, cipherColumn, null);
                            return new SubstitutableColumnNameToken(c.getColumnName().getStartIndex(), c.getColumnName().getStopIndex(), Collections.singleton(columnProjection));
                        }).orElseGet(() -> {
                            ColumnProjection columnProjection = new ColumnProjection(owner, c.getColumnName().getQualifiedName(), null);
                            return new SubstitutableColumnNameToken(c.getColumnName().getStartIndex(), c.getColumnName().getStopIndex(), Collections.singleton(columnProjection));
                        });
                    }).ifPresent(result::add);
        }
        return result;
    }

    private Collection<SQLToken> getAddColumnTokens(final String tableName, final AddColumnDefinitionSegment addColumnDefinitionSegment) {
        Collection<SQLToken> result = new LinkedList<>();
        for (ColumnDefinitionSegment each : addColumnDefinitionSegment.getColumnDefinitions()) {
            String columnName = each.getColumnName().getIdentifier().getValue();
            Optional<EncryptAlgorithm> encryptor = getEncryptRule().findEncryptor(tableName, columnName);
            if (encryptor.isPresent()) {
                result.addAll(getAddColumnTokens(tableName, columnName, addColumnDefinitionSegment, each));
            }
        }
        return result;
    }

    private Collection<SQLToken> getAddColumnTokens(final String tableName, final String columnName,
                                                    final AddColumnDefinitionSegment addColumnDefinitionSegment, final ColumnDefinitionSegment columnDefinitionSegment) {
        Collection<SQLToken> result = new LinkedList<>();
        result.add(new RemoveToken(addColumnDefinitionSegment.getStartIndex() - 1, columnDefinitionSegment.getStopIndex() + 1));
        result.add(getCipherColumn(tableName, columnName, columnDefinitionSegment));
        getAssistedQueryColumn(tableName, columnName, columnDefinitionSegment).ifPresent(result::add);
        getPlainColumn(tableName, columnName, columnDefinitionSegment).ifPresent(result::add);
        return result;
    }

    private Collection<SQLToken> getModifyColumnTokens(final String tableName, final Collection<ModifyColumnDefinitionSegment> columnDefinitionSegments) {
        Collection<SQLToken> result = new LinkedList<>();
        for (ModifyColumnDefinitionSegment each : columnDefinitionSegments) {
            ColumnDefinitionSegment segment = each.getColumnDefinition();
            String columnName = segment.getColumnName().getIdentifier().getValue();
            Optional<EncryptAlgorithm> encryptor = getEncryptRule().findEncryptor(tableName, columnName);
            Optional<EncryptAlgorithm> encryptorPrevious = getEncryptRule().findEncryptor(tableName,
                    Optional.ofNullable(each.getPreviousColumnDefinition()).map(columnDefinitionSegment -> columnDefinitionSegment.getQualifiedName()).orElse(""));
            boolean hadEncryptAfterCol = each.getColumnPosition().map(c -> c.getColumnName().getQualifiedName())
                    .map(c -> getEncryptRule().findEncryptor(tableName, c)).isPresent();
            if (encryptor.isPresent() || encryptorPrevious.isPresent() || hadEncryptAfterCol) {
                result.addAll(getModifyColumnTokens(tableName, columnName, each, segment));
            }
//            each.getColumnPosition().filter(c -> c instanceof ColumnAfterPositionSegment)
//            .map(c ->(ColumnAfterPositionSegment)c)
//            .filter(c -> getEncryptRule().findEncryptor(tableName, c.getColumnName().getQualifiedName()).isPresent())
//            .ifPresent(c -> {
//                ColumnSegment colSeg = c.getColumnName();
//                String owner = colSeg.getOwner().map(OwnerSegment::getIdentifier).map(IdentifierValue::getValue).orElse(null);
//                ColumnProjection columnProjection = new ColumnProjection(owner, colSeg.getQualifiedName(), null);
//                result.add(new SubstitutableColumnNameToken(colSeg.getStartIndex(), colSeg.getStopIndex(), Collections.singleton(columnProjection)));
//            });
        }
        return result;
    }

    private Collection<SQLToken> getModifyColumnTokens(final String tableName, final String columnName,
                                                       final ModifyColumnDefinitionSegment modifyColumnDefinitionSegment, final ColumnDefinitionSegment columnDefinitionSegment) {
        //        result.add(new RemoveToken(modifyColumnDefinitionSegment.getStartIndex() - 1, modifyColumnDefinitionSegment.getStopIndex()));
        Collection<SQLToken> result = new LinkedList<>(getCipherColumn(tableName, columnName, modifyColumnDefinitionSegment, columnDefinitionSegment));
        modifyColumnDefinitionSegment.getColumnPosition().flatMap(c -> getAfterColumn(tableName, c.getColumnName())).ifPresent(result::add);
        getAssistedQueryColumn(tableName, columnName, modifyColumnDefinitionSegment, columnDefinitionSegment).ifPresent(result::add);
        getPlainColumn(tableName, columnName, modifyColumnDefinitionSegment, columnDefinitionSegment).ifPresent(result::add);
        return result;
    }

    private Optional<SQLToken> getAfterColumn(String tableName, ColumnSegment columnName) {
        return getEncryptRule().findEncryptor(tableName, columnName.getQualifiedName())
                .map(e -> {
                    String cipherColumn = getEncryptRule().getCipherColumn(tableName, columnName.getQualifiedName());
                    return new EncryptAlterTableToken(columnName.getStartIndex(), columnName.getStopIndex(), cipherColumn, "");
                });
    }

    private Collection<SQLToken> getDropColumnTokens(final String tableName, final Collection<DropColumnDefinitionSegment> columnDefinitionSegments) {
        Collection<SQLToken> result = new LinkedList<>();
        for (DropColumnDefinitionSegment each : columnDefinitionSegments) {
            result.addAll(getDropColumnTokens(tableName, each));
        }
        return result;
    }

    private Collection<SQLToken> getDropColumnTokens(final String tableName, final DropColumnDefinitionSegment dropColumnDefinitionSegment) {
        Collection<SQLToken> result = new LinkedList<>();
        for (ColumnSegment each : dropColumnDefinitionSegment.getColumns()) {
            String columnName = each.getQualifiedName();
            Optional<EncryptAlgorithm> encryptor = getEncryptRule().findEncryptor(tableName, columnName);
            if (encryptor.isPresent()) {
                result.addAll(getDropColumnTokens(tableName, columnName, each, dropColumnDefinitionSegment));
            } else {
                result.add(new RemoveToken(dropColumnDefinitionSegment.getStartIndex() - 1, each.getStopIndex()));
                result.add(new EncryptAlterTableToken(each.getStopIndex() + 1, each.getStopIndex(), columnName, "DROP COLUMN"));
            }
        }
        return result;
    }

    private Collection<SQLToken> getDropColumnTokens(final String tableName, final String columnName,
                                                     final ColumnSegment columnSegment, final DropColumnDefinitionSegment dropColumnDefinitionSegment) {
        Collection<SQLToken> result = new LinkedList<>();
        result.add(new RemoveToken(dropColumnDefinitionSegment.getStartIndex() - 1, columnSegment.getStopIndex()));
        result.add(getCipherColumn(tableName, columnName, columnSegment));
        getAssistedQueryColumn(tableName, columnName, columnSegment).ifPresent(result::add);
        getPlainColumn(tableName, columnName, columnSegment).ifPresent(result::add);
        return result;
    }

    private EncryptAlterTableToken getCipherColumn(final String tableName, final String columnName, final ColumnDefinitionSegment columnDefinitionSegment) {
        String cipherColumn = getEncryptRule().getCipherColumn(tableName, columnName);
        return new EncryptAlterTableToken(columnDefinitionSegment.getStopIndex() + 1, columnDefinitionSegment.getStartIndex() + columnName.length(), cipherColumn, "ADD COLUMN");
    }

    private List<EncryptAlterTableToken> getCipherColumn(final String tableName, final String columnName,
                                                         final ModifyColumnDefinitionSegment modifyColumnDefinitionSegment, final ColumnDefinitionSegment columnDefinitionSegment) {
        String colCipherName = getEncryptRule().findEncryptor(tableName, columnName).map(c -> getEncryptRule().getCipherColumn(tableName, columnName)).orElse(columnName);
        ColumnSegment previousColDef = modifyColumnDefinitionSegment.getPreviousColumnDefinition();
        String beforeColCipherName = Optional.ofNullable(previousColDef).map(segment -> segment.getQualifiedName())
                .map(c -> getEncryptRule().findEncryptor(tableName, columnName).map(cc-> getEncryptRule().getCipherColumn(tableName, c)).orElse(c)).orElse("");
        String encryptCommand = "";
        List<EncryptAlterTableToken> res = new ArrayList<>();
        if (!Strings.isNullOrEmpty(colCipherName)){
            res.add(new EncryptAlterTableToken(columnDefinitionSegment.getColumnName().getStartIndex(), columnDefinitionSegment.getColumnName().getStopIndex(), colCipherName, encryptCommand));
        }
        if (!Strings.isNullOrEmpty(beforeColCipherName)){
            res.add(new EncryptAlterTableToken(previousColDef.getStartIndex(), previousColDef.getStopIndex(), beforeColCipherName, encryptCommand));
        }
        return res;
    }

    private EncryptAlterTableToken getCipherColumn(final String tableName, final String columnName, final ColumnSegment columnSegment) {
        String cipherColumn = getEncryptRule().getCipherColumn(tableName, columnName);
        return new EncryptAlterTableToken(columnSegment.getStopIndex() + 1, columnSegment.getStopIndex(), cipherColumn, "DROP COLUMN");
    }

    private Optional<EncryptAlterTableToken> getAssistedQueryColumn(final String tableName, final String columnName, final ColumnDefinitionSegment columnDefinitionSegment) {
        Optional<String> assistedQueryColumn = getEncryptRule().findAssistedQueryColumn(tableName, columnName);
        return assistedQueryColumn.map(optional -> new EncryptAlterTableToken(
                columnDefinitionSegment.getStopIndex() + 1, columnDefinitionSegment.getStartIndex() + columnName.length(), optional, ", ADD COLUMN"));
    }

    private Optional<EncryptAlterTableToken> getAssistedQueryColumn(final String tableName, final String columnName,
                                                                    final ModifyColumnDefinitionSegment modifyColumnDefinitionSegment, final ColumnDefinitionSegment columnDefinitionSegment) {
        String previousColumnName = Optional.ofNullable(modifyColumnDefinitionSegment.getPreviousColumnDefinition()).map(segment -> segment.getQualifiedName()).orElse("");
        Optional<String> assistedQueryColumnPrevious = (!previousColumnName.isEmpty())
                ? getEncryptRule().findAssistedQueryColumn(tableName, previousColumnName) : Optional.of("");
        String encryptColumnName = assistedQueryColumnPrevious.orElse("").isEmpty()
                ? getEncryptRule().findAssistedQueryColumn(tableName, columnName).orElse("") : columnDefinitionSegment.getColumnName().getQualifiedName() + "_assisted";
        String encryptCommand ="";
        if (Strings.isNullOrEmpty(encryptColumnName)){
            return Optional.empty();
        }
        return Optional.of(new EncryptAlterTableToken(
                modifyColumnDefinitionSegment.getStopIndex() + 1, columnDefinitionSegment.getDataType().getStartIndex() - 1, encryptColumnName, encryptCommand));
    }

    private Optional<EncryptAlterTableToken> getAssistedQueryColumn(final String tableName, final String columnName, final ColumnSegment columnSegment) {
        Optional<String> assistedQueryColumn = getEncryptRule().findAssistedQueryColumn(tableName, columnName);
        return assistedQueryColumn.map(optional -> new EncryptAlterTableToken(columnSegment.getStopIndex() + 1, columnSegment.getStopIndex(), optional, ", DROP COLUMN"));
    }

    private Optional<EncryptAlterTableToken> getPlainColumn(final String tableName, final String columnName, final ColumnDefinitionSegment columnDefinitionSegment) {
        Optional<String> plainColumn = getEncryptRule().findPlainColumn(tableName, columnName);
        return plainColumn.map(optional -> new EncryptAlterTableToken(
                columnDefinitionSegment.getStopIndex() + 1, columnDefinitionSegment.getStartIndex() + columnName.length(), optional, ", ADD COLUMN"));
    }

    private Optional<EncryptAlterTableToken> getPlainColumn(final String tableName, final String columnName,
                                                            final ModifyColumnDefinitionSegment modifyColumnDefinitionSegment, final ColumnDefinitionSegment columnDefinitionSegment) {
        String previousColumnName = Optional.ofNullable(modifyColumnDefinitionSegment.getPreviousColumnDefinition()).map(segment -> segment.getQualifiedName()).orElse("");
        Optional<String> plainColumnPrevious = (!previousColumnName.isEmpty())
                ? getEncryptRule().findPlainColumn(tableName, previousColumnName) : Optional.of("");
        String encryptColumnName = plainColumnPrevious.orElse("").isEmpty()
                ? getEncryptRule().findPlainColumn(tableName, columnName).orElse("") : columnDefinitionSegment.getColumnName().getQualifiedName() + "_plain";
        String encryptCommand = "";
        if (Strings.isNullOrEmpty(encryptColumnName)) {
            return Optional.empty();
        }
        return Optional.of(new EncryptAlterTableToken(
                columnDefinitionSegment.getColumnName().getStartIndex(), columnDefinitionSegment.getColumnName().getStopIndex(), encryptColumnName, encryptCommand));
    }

    private Optional<EncryptAlterTableToken> getPlainColumn(final String tableName, final String columnName, final ColumnSegment columnSegment) {
        Optional<String> plainColumn = getEncryptRule().findPlainColumn(tableName, columnName);
        return plainColumn.map(optional -> new EncryptAlterTableToken(columnSegment.getStopIndex() + 1, columnSegment.getStopIndex(), optional, ", DROP COLUMN"));
    }
}
