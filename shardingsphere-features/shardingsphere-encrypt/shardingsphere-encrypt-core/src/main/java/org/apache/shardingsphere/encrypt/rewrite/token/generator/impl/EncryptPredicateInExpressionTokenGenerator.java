package org.apache.shardingsphere.encrypt.rewrite.token.generator.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.shardingsphere.encrypt.rewrite.token.generator.BaseEncryptSQLTokenGenerator;
import org.apache.shardingsphere.encrypt.spi.EncryptAlgorithm;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ColumnProjection;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.metadata.schema.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.aware.SchemaMetaDataAware;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.SQLToken;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.ExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.InExpression;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.ListExpression;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.expr.complex.CommonExpressionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.predicate.WhereSegment;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jarod.Kong
 */
@Setter
public class EncryptPredicateInExpressionTokenGenerator extends BaseEncryptSQLTokenGenerator implements CollectionSQLTokenGenerator, SchemaMetaDataAware {

    private ShardingSphereSchema schema;

    @Override
    protected boolean isGenerateSQLTokenForEncrypt(SQLStatementContext sqlStatementContext) {
        Optional<InExpression> inExprLeftRight = matchSQLToken(sqlStatementContext);
        return inExprLeftRight.isPresent();
    }

    private Optional<InExpression> matchSQLToken(SQLStatementContext sqlStatementContext) {
        if (!(sqlStatementContext instanceof SelectStatementContext)) {
            return Optional.empty();
        }
        SelectStatementContext selectStatementContext = (SelectStatementContext) sqlStatementContext;
        Optional<ExpressionSegment> expressionSegment = selectStatementContext.getWhere().map(WhereSegment::getExpr);
        if (!expressionSegment.filter(e -> e instanceof InExpression).isPresent()) {
            return Optional.empty();
        }
        // 复合表达式的in
        return expressionSegment.map(e -> (InExpression) e)
                .filter(e -> e.getLeft() instanceof CommonExpressionSegment && e.getRight() instanceof ListExpression);
    }

    @Override
    public Collection<? extends SQLToken> generateSQLTokens(SQLStatementContext sqlStatementContext) {
        matchSQLToken(sqlStatementContext)
        .ifPresent(c -> {
            throw new UnsupportedOperationException("When InExpression had Encrypt Column, Proxy Unsupported InExpression combination operation");
        });
        InExpression inExpression = matchSQLToken(sqlStatementContext).get();
        // TODO: 2021/12/14 需要转为带index的Segment 和 token才可以，目前返回不支持错误
        // left
        CommonExpressionSegment left = (CommonExpressionSegment) inExpression.getLeft();
        List<ColumnProjection> leftCols = getColProjectionsByCommonExprSeg(left);
        Map<String, String> columnTableNames = sqlStatementContext.getTablesContext().findTableName(leftCols, schema);
        List<ColInfo> LeftColInfos = getColAndEncryptAlgos(leftCols, columnTableNames);
        if (LeftColInfos.stream().allMatch(c -> c.getEncryptAlgo() == null)) {
            return Collections.emptyList();
        }
        // right
        ListExpression right = (ListExpression) inExpression.getRight();
        List<List<ColumnProjection>> rightCols = right.getItems().stream().map(e -> (CommonExpressionSegment) e).map(c -> getColProjectionsByCommonExprSeg(c)).collect(Collectors.toList());
        for (List<ColumnProjection> rightCol : rightCols) {
            for (int i = 0; i < rightCol.size(); i++) {
                ColumnProjection rCol = rightCol.get(i);
                ColInfo lCol = LeftColInfos.get(i);
                if (lCol.getEncryptAlgo() != null) {
                    String fieldVal = rCol.getName();
                    @SuppressWarnings("unchecked")
                    Object encryptVal = lCol.getEncryptAlgo().encrypt(fieldVal);
                    ColumnProjection newRColProject = new ColumnProjection(rCol.getOwner(), Objects.toString(encryptVal, null), rCol.getAlias().orElse(null));
                    rightCol.set(i, newRColProject);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<ColInfo> getColAndEncryptAlgos(List<ColumnProjection> leftCols, Map<String, String> columnTableNames) {
        // 字段找表名、判断是否存在加密字段
        List<ColInfo> colInfos = leftCols.stream().map(col -> {
            Optional<String> tableNameOpt = Optional.ofNullable(columnTableNames.get(col.getExpression()));
            EncryptAlgorithm encryptAlgorithm = tableNameOpt.map(tableName -> {
                Optional<EncryptAlgorithm> encryptor = getEncryptRule().findEncryptor(tableName, col.getName());
                return encryptor.orElse(null);
            }).orElse(null);
            return new ColInfo(tableNameOpt.orElse(null), col, encryptAlgorithm);
        }).map(c -> {
            if (c.getEncryptAlgo() != null) {
                ColumnProjection col = c.getCol();
                String cipherColumn = getEncryptRule().getCipherColumn(c.getTableName(), c.getCol().getName());
                return new ColInfo(c.getTableName(), new ColumnProjection(col.getOwner(), cipherColumn, col.getAlias().orElse(null)), c.getEncryptAlgo());
            }
            return c;
        }).collect(Collectors.toList());
        return colInfos;
    }

    @Getter
    @RequiredArgsConstructor
    private static class ColInfo{
        private final String tableName;
        private final ColumnProjection col;
        private final EncryptAlgorithm encryptAlgo;
    }

    private List<ColumnProjection> getColProjectionsByCommonExprSeg(CommonExpressionSegment left) {
        String[] fieldArr = left.getText().substring(1, left.getText().length() - 1).split(",");
        return Arrays.stream(fieldArr).map(f -> {
            String[] ownerAndFieldName = f.split("\\.");
            String owner = "", fieldName = "";
            if (ownerAndFieldName.length == 1) {
                fieldName = ownerAndFieldName[0];
            }
            if (ownerAndFieldName.length >= 2) {
                owner = ownerAndFieldName[0];
                fieldName = ownerAndFieldName[1];
            }
            return new ColumnProjection(owner, fieldName, null);
        }).collect(Collectors.toList());
    }

    @Override
    public void setSchema(ShardingSphereSchema schema) {

    }
}
