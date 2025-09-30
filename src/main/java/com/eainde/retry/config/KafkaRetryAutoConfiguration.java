package com.eainde.retry.config;

import com.eainde.retry.ExceptionRetryabilityChecker;
import com.eainde.retry.RetryOrchestrator;
import com.eainde.retry.RetryQualifierResolver;
import com.eainde.retry.repository.FailedMessageRepository;
import com.eainde.retry.scheduler.RetryScheduler;
import com.eainde.retry.service.KafkaRetryService;
import com.eainde.retry.service.RetryMessageHandler;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@ConditionalOnProperty(name = "kafka.retry.enabled", havingValue = "true")
@EnableConfigurationProperties(KafkaRetryProperties.class)
@EnableJpaRepositories(basePackages = "com.example.kafkaretry.repository")
@EntityScan(basePackages = "com.example.kafkaretry.model")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class KafkaRetryAutoConfiguration {

    /**
     * Configures the LockProvider for ShedLock using a JDBC template.
     * This ensures that scheduled tasks are executed by only one node in a clustered environment.
     *
     * @param dataSource The application's main data source.
     * @return A configured LockProvider bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // Uses the database time, which is recommended for clusters.
                        .build()
        );
    }

    /**
     * NEW: Creates the central resolver bean for mapping topics to handlers.
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryQualifierResolver retryQualifierResolver(KafkaRetryProperties properties) {
        return new RetryQualifierResolver(properties);
    }

    /**
     * Creates the RetryScheduler bean if a RetryMessageHandler is defined by the consuming application.
     * The scheduler is the core component that finds and processes failed messages.
     *
     * @param messageHandler The custom implementation provided by the consuming service.
     * @param repository The repository for accessing failed messages.
     * @param properties The configured retry properties.
     * @return The RetryScheduler bean.
     */
    @Bean
    @ConditionalOnBean(RetryMessageHandler.class)
    @ConditionalOnMissingBean
    public RetryScheduler retryScheduler(RetryMessageHandler messageHandler,
                                         FailedMessageRepository repository,
                                         KafkaRetryProperties properties) {
        return new RetryScheduler(messageHandler, repository, properties);
    }

    /**
     * Creates the KafkaRetryService bean, which provides a simple way
     * for consuming applications to save new failed messages.
     *
     * @param repository The repository for persisting failed messages.
     * @return The KafkaRetryService bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaRetryService kafkaRetryService(FailedMessageRepository repository) {
        return new KafkaRetryService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExceptionRetryabilityChecker exceptionRetryabilityChecker(KafkaRetryProperties properties) {
        return new ExceptionRetryabilityChecker(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryOrchestrator retryOrchestrator(KafkaRetryService retryService,
                                               RetryQualifierResolver qualifierResolver,
                                               ExceptionRetryabilityChecker retryabilityChecker) {
        return new RetryOrchestrator(retryService, qualifierResolver, retryabilityChecker);
    }
}

