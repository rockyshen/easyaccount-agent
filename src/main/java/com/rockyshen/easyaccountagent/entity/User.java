package com.rockyshen.easyaccountagent.entity;

import lombok.Data;

@Data
public class User {
    private int id;
    private String name;
    /** 明文密码字符串（支持字母大小写与符号）；哈希升级另议 */
    private String password;
}
