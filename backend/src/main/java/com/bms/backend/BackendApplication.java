package com.bms.backend;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		boolean bootstrapAdmin = Boolean.parseBoolean(
				System.getenv().getOrDefault("BMS_BOOTSTRAP_ADMIN_ENABLED", "false"))
				|| Arrays.asList(args).contains("--bms.bootstrap-admin.enabled=true");

		SpringApplication application = new SpringApplication(BackendApplication.class);
		if (bootstrapAdmin) {
			application.setWebApplicationType(WebApplicationType.NONE);
		}

		ConfigurableApplicationContext context = application.run(args);
		if (bootstrapAdmin) {
			context.close();
		}
	}

}
