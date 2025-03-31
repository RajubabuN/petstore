package com.chtrembl.petstoreapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
public class PetstoreappApplication {
	private static Logger logger = LoggerFactory.getLogger(PetstoreappApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PetstoreappApplication.class, args);
		logger.info("PetStoreApp started up... " + System.getProperty("catalina.base"));
		try {
			InetAddress address = InetAddress.getByName("www.google.com");
			logger.info("GOOGLE: " + address.getHostAddress());
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}
}
