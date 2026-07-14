package com.rockyshen.easyaccountagent.dto;

public class LoginRequestDto {
    private String name;
    private Integer password;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPassword() {
        return password;
    }

    public void setPassword(Integer password) {
        this.password = password;
    }
}
