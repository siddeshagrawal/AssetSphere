package com.assetsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic
public class AssetSphereApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssetSphereApplication.class, args);
    }
}
