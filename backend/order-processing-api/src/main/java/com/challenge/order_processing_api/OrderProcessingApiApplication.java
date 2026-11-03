package com.challenge.order_processing_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderProcessingApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderProcessingApiApplication.class, args);
	}

}
