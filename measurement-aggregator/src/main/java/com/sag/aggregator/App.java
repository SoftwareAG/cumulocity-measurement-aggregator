package com.sag.aggregator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;


@MicroserviceApplication
public class App {

	@Autowired
	RestService restController;
	
	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}
}