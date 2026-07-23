package com.bms.backend.system.application.bootstrap;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bms.bootstrap-admin.enabled", havingValue = "true")
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminInputProvider inputProvider;
    private final BootstrapAdminService bootstrapAdminService;

    @Override
    public void run(ApplicationArguments args) {
        BootstrapAdminInput input = inputProvider.load();
        String userId = bootstrapAdminService.bootstrap(input);
        log.info(
                "Initial system administrator bootstrap completed successfully. userId={}",
                userId);
    }
}
