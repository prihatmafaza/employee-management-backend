package org.emb.accessrequests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AccessRequestsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessRequestsApplication.class, args);
    }
}
