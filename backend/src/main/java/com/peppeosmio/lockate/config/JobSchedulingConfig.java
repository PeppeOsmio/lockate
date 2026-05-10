package com.peppeosmio.lockate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Profile("default")
@Configuration
@EnableScheduling
public class JobSchedulingConfig {}

