package com.schwab.nms.bdd;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(
        properties = "spring.task.scheduling.enabled=false"
)
@ActiveProfiles("test")
public class CucumberSpringConfiguration {
}