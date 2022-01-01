package org.apache.shardingsphere.sql.parser.sql.dialect.statement.mysql.dal;

import lombok.ToString;
import org.apache.shardingsphere.sql.parser.sql.common.statement.AbstractSQLStatement;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dal.DALStatement;
import org.apache.shardingsphere.sql.parser.sql.dialect.statement.mysql.MySQLStatement;

/**
 * @author Jarod.Kong
 */
@ToString
public class MySQLShowPluginsStatement extends AbstractSQLStatement implements DALStatement, MySQLStatement {
}
