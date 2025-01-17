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

package org.apache.shardingsphere.infra.binder.segment.select.projection.impl;

import lombok.*;
import org.apache.shardingsphere.infra.binder.segment.select.projection.Projection;

import java.util.Optional;

/**
 * Common projection.
 */
@RequiredArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public final class ColumnProjection implements Projection {
    
    private final String owner;
    
    private final String name;
    
    private final String alias;

    @Setter
    private Boolean subqueryProjectionEqual = Boolean.FALSE;
    
    @Override
    public String getExpression() {
        return null == owner ? name : owner + "." + name;
    }
    
    @Override
    public String getColumnLabel() {
        return getAlias().orElse(name);
    }
    
    @Override
    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }
    
    /**
     * Get expression with alias.
     * 
     * @return expression with alias
     */
    public String getExpressionWithAlias() {
        return getExpression() + (null == alias ? "" : " AS " + alias);
    }
}
