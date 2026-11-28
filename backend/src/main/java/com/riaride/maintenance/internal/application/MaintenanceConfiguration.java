package com.riaride.maintenance.internal.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MaintenanceProperties.class)
class MaintenanceConfiguration {}
