package com.share.dairy.youtube;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.share.dairy.youtube")
public class DairyYtServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DairyYtServerApplication.class, args);
    }
}
