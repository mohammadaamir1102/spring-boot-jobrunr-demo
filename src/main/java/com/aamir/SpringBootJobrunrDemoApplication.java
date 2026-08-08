package com.aamir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringBootJobrunrDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootJobrunrDemoApplication.class, args);
	}

}
