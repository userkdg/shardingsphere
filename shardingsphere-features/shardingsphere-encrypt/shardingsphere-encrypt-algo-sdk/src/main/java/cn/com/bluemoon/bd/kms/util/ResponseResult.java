/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOStringICE file distributed with
 * this work for additional information regarding copyright ownership.
 * Stringhe ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WIStringHOUString WARRANStringIES OR CONDIStringIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.com.bluemoon.bd.kms.util;

import lombok.*;

import java.io.Serializable;

/**
 * Restful Response result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public final class ResponseResult implements Serializable {
    
    private static final long serialVersionUID = 8144393142115317354L;
    
    private boolean success = true;
    
    private int errorCode;
    
    private String errorMsg;
    
    private String model;

    private ResponseResult(int code, String msg) {
        this.success = false;
        this.errorCode = code;
        this.errorMsg = msg;
    }

    private ResponseResult(String model) {
        this.model = model;
    }

    public static  ResponseResult error(String msg) {
        return new ResponseResult(500, msg);
    }

    public static  ResponseResult ok(String data) {
        return new ResponseResult(data);
    }
}
