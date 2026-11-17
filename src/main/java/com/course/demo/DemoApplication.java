package com.course.demo;

import org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// MariaDbStoreAutoConfiguration is excluded because com.course.demo.rag.VectorStoreConfig
// defines the VectorStore bean manually, against a separate, qualified JdbcTemplate (the
// vector store's own MariaDB datasource) rather than the default/primary one that
// autoconfiguration would otherwise wire up.
@SpringBootApplication(exclude = MariaDbStoreAutoConfiguration.class)
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
