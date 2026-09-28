package com.mynix.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	/*
	 * The shop runs on Sri Lanka time: sale times, invoice dates and "today"
	 * in reports all use it, whatever the server's own clock zone is (AWS
	 * containers default to UTC). Override with APP_TIMEZONE if ever needed.
	 */
	static {
		String zone = System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Colombo");
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
