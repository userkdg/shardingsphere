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

package org.apache.shardingsphere.sql.parser.core.database.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.apache.shardingsphere.sql.parser.api.parser.SQLParser;
import org.apache.shardingsphere.sql.parser.core.ParseASTNode;
import org.apache.shardingsphere.sql.parser.core.ParseContext;
import org.apache.shardingsphere.sql.parser.core.SQLParserFactory;
import org.apache.shardingsphere.sql.parser.exception.SQLParsingException;
import org.apache.shardingsphere.sql.parser.spi.DatabaseTypedSQLParserFacade;

/**
 * SQL parser executor.
 */
@Slf4j
@RequiredArgsConstructor
public final class SQLParserExecutor {
    
    private final String databaseType;
    
    private final boolean sqlCommentParseEnabled;
    
    /**
     * Parse SQL.
     * 
     * @param sql SQL to be parsed
     * @return parse context
     */
    public ParseContext parse(final String sql) {
        ParseASTNode result = twoPhaseParse(sql);
        if (result.getRootNode() instanceof ErrorNode) {
            throw new SQLParsingException("Unsupported SQL of `%s`", sql);
        }
        return new ParseContext(result.getRootNode(), result.getHiddenTokens());
    }
    
    private ParseASTNode twoPhaseParse(final String sql) {
        DatabaseTypedSQLParserFacade sqlParserFacade = DatabaseTypedSQLParserFacadeRegistry.getFacade(databaseType);
        SQLParser sqlParser = SQLParserFactory.newInstance(sql, sqlParserFacade.getLexerClass(), sqlParserFacade.getParserClass(), sqlCommentParseEnabled);
        try {
            ((Parser) sqlParser).getInterpreter().setPredictionMode(PredictionMode.SLL);
            return (ParseASTNode) sqlParser.parse();
        } catch (final ParseCancellationException ex) {
            ((Parser) sqlParser).reset();
            ((Parser) sqlParser).getInterpreter().setPredictionMode(PredictionMode.LL);
            try {
                return (ParseASTNode) sqlParser.parse();
            } catch (final ParseCancellationException e) {
                if (sql.trim().toLowerCase().startsWith("preview")){
                    log.debug("注：解析中忽略preview解析失败的异常，屏蔽生产级别日志输出");
                }else {
                    log.error("[注：若为distSQL解析异常，则可忽略]sql={}, throw ParseCancellationException: You have an error in your SQL syntax", sql);
                }
                throw new SQLParsingException("You have an error in your SQL syntax");
            }
        }
    }
}
