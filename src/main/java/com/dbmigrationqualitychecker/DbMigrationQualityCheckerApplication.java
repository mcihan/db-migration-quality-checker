package com.dbmigrationqualitychecker;

import com.dbmigrationqualitychecker.check.CheckRunner;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class DbMigrationQualityCheckerApplication {

    private final CheckRunner checkRunner;

    public static void main(String[] args) {
        SpringApplication.run(DbMigrationQualityCheckerApplication.class, args);
    }

    @Bean
    public ApplicationRunner runChecks() {
        return args -> {
            Instant start = Instant.now();
            checkRunner.runAll();
            log.info("***********************************************");
            log.info("All checks completed. Duration: {}", format(Duration.between(start, Instant.now())));
            log.info("***********************************************");
        };
    }

    private static String format(Duration d) {
        return String.format("%dm %ds", d.toMinutes(), d.getSeconds() % 60);
    }
}
