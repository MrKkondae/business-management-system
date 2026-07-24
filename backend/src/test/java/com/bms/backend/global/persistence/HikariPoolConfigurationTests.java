package com.bms.backend.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "spring.main.web-application-type=none",
            "spring.datasource.url=jdbc:h2:mem:hikari-pool-configuration;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.datasource.hikari.connection-init-sql=",
            "spring.flyway.enabled=false",
            "BMS_DB_POOL_MAX_SIZE=10",
            "BMS_DB_POOL_MIN_IDLE=2",
            "BMS_DB_CONNECTION_TIMEOUT_MS=5000",
            "BMS_DB_VALIDATION_TIMEOUT_MS=2000",
            "BMS_DB_IDLE_TIMEOUT_MS=600000",
            "BMS_DB_MAX_LIFETIME_MS=1800000",
            "BMS_DB_KEEPALIVE_TIME_MS=0",
            "BMS_DB_LEAK_DETECTION_THRESHOLD_MS=0"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HikariPoolConfigurationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void bindsTheExternalizedSingleInstancePoolBaseline() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);

        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(10);
        assertThat(hikari.getMinimumIdle()).isEqualTo(2);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(5_000);
        assertThat(hikari.getValidationTimeout()).isEqualTo(2_000);
        assertThat(hikari.getIdleTimeout()).isEqualTo(600_000);
        assertThat(hikari.getMaxLifetime()).isEqualTo(1_800_000);
        assertThat(hikari.getKeepaliveTime()).isZero();
        assertThat(hikari.getLeakDetectionThreshold()).isZero();
    }
}
