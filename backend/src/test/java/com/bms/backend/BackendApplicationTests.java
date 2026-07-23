package com.bms.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class BackendApplicationTests {

	@Test
	void applicationHasSpringBootEntryPoint() {
		assertThat(BackendApplication.class).hasAnnotation(SpringBootApplication.class);
	}

}
