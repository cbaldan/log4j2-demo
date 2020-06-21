package org.cbj.demo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class AppenderTest {
	
	@Test
	public void startLogger() {
		Logger logger = LogManager.getLogger(AppenderTest.class);
		logger.info("Application logging");
	}

}
