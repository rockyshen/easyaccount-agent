package com.rockyshen.easyaccountagent;

import com.rockyshen.easyaccountagent.auth.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class EasyAccountAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyAccountAgentApplication.class, args);
    }
}
