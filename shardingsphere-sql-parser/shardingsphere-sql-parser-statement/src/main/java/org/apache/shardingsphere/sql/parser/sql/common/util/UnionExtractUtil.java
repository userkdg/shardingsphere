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

package org.apache.shardingsphere.sql.parser.sql.common.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.union.UnionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.table.SubqueryTableSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.SelectStatement;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * union extract utility class.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UnionExtractUtil {

    /**
     * Get union segment from SelectStatement.
     *
     * @param selectStatement SelectStatement
     * @return subquery segment collection
     */
    public static Collection<UnionSegment> getUnionSegments(final SelectStatement selectStatement) {
        List<UnionSegment> result = new LinkedList<>();
        if (selectStatement.getFrom() instanceof SubqueryTableSegment) {
            SubqueryTableSegment subqueryTableSegment = (SubqueryTableSegment) selectStatement.getFrom();
            Collection<UnionSegment> unionSegments = subqueryTableSegment.getSubquery().getSelect().getUnionSegments();
            if (!unionSegments.isEmpty()){
                result.addAll(unionSegments);
                findUnionSegment0(result, unionSegments);
            }
        }else {
            Collection<UnionSegment> unionSegments = selectStatement.getUnionSegments();
            if (!unionSegments.isEmpty()){
                result.addAll(unionSegments);
                findUnionSegment0(result, unionSegments);
            }
        }
        return result;
    }

    private static void findUnionSegment0(List<UnionSegment> result, Collection<UnionSegment> unionSegments) {
        for (UnionSegment each : unionSegments) {
            SelectStatement stats = each.getSelectStatement();
            result.addAll(getUnionSegments(stats));
        }
    }

}
