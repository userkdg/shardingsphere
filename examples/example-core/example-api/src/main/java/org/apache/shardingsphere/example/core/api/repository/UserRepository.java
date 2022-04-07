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

package org.apache.shardingsphere.example.core.api.repository;

import org.apache.shardingsphere.example.core.api.entity.User;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface UserRepository extends CommonRepository<User, Long> {

    default void insertFixture1(User user) throws SQLException {

    }

    default void updateUsers(List<User> users) {

    }

    default List<User> selectFixture1(List<Long> userIdList) {
        return Collections.emptyList();
    }

    default List<User> selectFixture2() {
        return Collections.emptyList();
    }

    default  List<Map<String,Object>> selectFixture3() {
        return Collections.emptyList();
    }

    default  List<Map<String,Object>> selectFixture4() {
        return Collections.emptyList();
    }
}
