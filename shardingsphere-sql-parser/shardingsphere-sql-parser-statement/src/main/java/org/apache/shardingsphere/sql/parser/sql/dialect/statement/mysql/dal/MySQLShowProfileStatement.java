package org.apache.shardingsphere.sql.parser.sql.dialect.statement.mysql.dal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.shardingsphere.sql.parser.sql.common.statement.AbstractSQLStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dal.DALStatement;
import org.apache.shardingsphere.sql.parser.sql.dialect.statement.mysql.MySQLStatement;

import java.util.List;

/**
 * @author Jarod.Kong
 */
@RequiredArgsConstructor
@Getter
@ToString
public class MySQLShowProfileStatement extends AbstractSQLStatement implements DALStatement, MySQLStatement {
    private final List<String> types;
    private final String forQuery;
}
