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

package org.apache.shardingsphere.example.core.mybatis.service;

import lombok.SneakyThrows;
import org.apache.shardingsphere.example.core.api.entity.User;
import org.apache.shardingsphere.example.core.api.repository.UserRepository;
import org.apache.shardingsphere.example.core.api.service.ExampleService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service("encrypt")
public class UserServiceImpl implements ExampleService {

    @Resource
    private UserRepository userRepository;

    @Override
    public void initEnvironment() throws SQLException {
        userRepository.createTableIfNotExists();
        userRepository.truncateTable();
    }

    @Override
    public void cleanEnvironment() throws SQLException {
        userRepository.dropTable();
    }

    @Override
    public void processSuccess() throws SQLException {
        System.out.println("-------------- Process Success Begin ---------------");
        List<Long> userIds = insertData();
        printData();
        selectData(userIds);
        updateData(userRepository.selectAll());
        printData();
        deleteData(userIds);
        printData();
        System.out.println("-------------- Process Success Finish --------------");
    }

    private void selectData(List<Long> userIds) {
        selectFixture4();
        selectFixture3();
        selectFixture1();
        selectFixture2();
    }

    private void selectFixture3() {
        List<Map<String,Object>> res = userRepository.selectFixture3();
        System.out.println(res);
    }
    private void selectFixture4() {
        List<Map<String,Object>> res = userRepository.selectFixture4();
        System.out.println(res);
    }
    private void selectFixture2() {
        userRepository.selectFixture2();
    }

    /**
     * 结论要调大-Xss的空间大小，eg:-Xss50M
     */
    private void selectFixture1() {
        // select fixture
        int capacity = 2000;
        List<Long> userIds = new ArrayList<>(capacity);
        for (int i = 0; i < capacity; i++) {
            userIds.add((long) i);
        }
        System.out.println("-------------ing:selectFixture1-------------------");
        List<User> users = userRepository.selectFixture1(userIds);
        for (User each : users) {
            System.out.println(each);
        }
        System.out.println("-------------ok:selectFixture1-------------------");
    }

    @SneakyThrows
    private void updateData( List<User> users) {
        List<String> sql = new ArrayList<>();
        for (User user : users) {
            sql.add(String.format("update t_user set user_name='%s', pwd='%s' where user_id=%d", user.getUserName()+"_update", user.getPwd()+"_update", user.getUserId()));
            user.setUserName(user.getUserName()+"_update");
            user.setPwd(user.getPwd()+"_update");
        }
        String actualSql = String.join(";", sql);
        System.out.println(actualSql);
        userRepository.updateUsers(Collections.singletonList(users.get(0)));
        userRepository.updateUsers(users);
    }

    private List<Long> insertData() throws SQLException {
        System.out.println("---------------------------- Insert Data ----------------------------");
        List<Long> result = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            User user = new User();
            user.setUserId(i);
            user.setUserName("test_mybatis_" + i);
            user.setPwd("pwd_mybatis_" + i);
            userRepository.insert(user);
            result.add((long) user.getUserId());
        }
        return result;
    }

    @Override
    public void processFailure() throws SQLException {
        System.out.println("-------------- Process Failure Begin ---------------");
        insertData();
        System.out.println("-------------- Process Failure Finish --------------");
        throw new RuntimeException("Exception occur for transaction test.");
    }

    private void deleteData(final List<Long> userIds) throws SQLException {
        System.out.println("---------------------------- Delete Data ----------------------------");
        for (Long each : userIds) {
            userRepository.delete(each);
        }
    }

    @Override
    public void printData() throws SQLException {
        System.out.println("---------------------------- Print User Data -----------------------");
        for (Object each : userRepository.selectAll()) {
            System.out.println(each);
        }
    }
}
