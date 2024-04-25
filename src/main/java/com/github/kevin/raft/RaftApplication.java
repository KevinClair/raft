package com.github.kevin.raft;

import com.github.kevin.raft.core.configuration.RaftConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(value = RaftConfigurationProperties.class)
public class RaftApplication {

	public static void main(String[] args) {
		SpringApplication.run(RaftApplication.class, args);
	}

}
