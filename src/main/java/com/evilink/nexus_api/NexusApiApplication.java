package com.evilink.nexus_api;

import com.evilink.nexus_api.mcpone.config.McpOneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.evilink.nexus_api.config.VSecretsProperties;



@SpringBootApplication
@EnableConfigurationProperties({McpOneProperties.class, VSecretsProperties.class})
public class NexusApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(NexusApiApplication.class, args);
	}

}
