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

package org.apache.shardingsphere.example.core.mybatis.repository;

import org.apache.ibatis.annotations.Param;
import org.apache.shardingsphere.example.core.api.entity.User;
import org.apache.shardingsphere.example.core.api.repository.UserRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface MybatisUserRepository extends UserRepository {

    void insertFixture1(@Param("user") User entity) throws SQLException;

    void updateUsers(@Param("userList") List<User> users);

    List<User> selectFixture1(@Param("userIdList")List<Long> userIdList);

    List<User> selectFixture2();

    List<Map<String,Object>> selectFixture3();

    List<Map<String,Object>> selectFixture4();

}
