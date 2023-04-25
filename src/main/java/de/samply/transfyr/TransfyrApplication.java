package de.samply.transfyr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TransfyrApplication {

	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(TransfyrApplication.class, args)));
	}

}
