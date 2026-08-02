package dev.forme.operations.orderimport.batch;

import java.time.OffsetDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class OrderImportBatchConfiguration {
    public static final int CHUNK_SIZE = 100;

    @Bean
    @JobScope
    JdbcCursorItemReader<StagedOrderRow> orderImportReader(
            DataSource dataSource,
            @Value("#{jobParameters['importJobId']}") String importJobId) {
        return new JdbcCursorItemReaderBuilder<StagedOrderRow>()
                .name("orderImportReader")
                .dataSource(dataSource)
                .fetchSize(CHUNK_SIZE)
                .saveState(true)
                .sql("""
                        SELECT r.id AS row_id, r.integration_job_id, r.source_order_id, r.ordered_at,
                               s.id AS sku_id, r.quantity, r.unit_price, r.currency,
                               r.recipient_name, r.postal_code, r.address_line1, r.address_line2
                          FROM order_import_rows r
                          JOIN skus s ON s.sku_code = r.sku_code AND s.active = TRUE
                         WHERE r.integration_job_id = ?::uuid
                           AND r.validation_status = 'VALID'
                           AND r.processing_status <> 'PROCESSED'
                         ORDER BY r.line_number
                        """)
                .preparedStatementSetter(statement -> statement.setString(1, importJobId))
                .rowMapper((resultSet, rowNumber) -> new StagedOrderRow(
                        resultSet.getObject("row_id", UUID.class),
                        resultSet.getObject("integration_job_id", UUID.class),
                        resultSet.getString("source_order_id"),
                        resultSet.getObject("ordered_at", OffsetDateTime.class),
                        resultSet.getObject("sku_id", UUID.class),
                        resultSet.getInt("quantity"), resultSet.getBigDecimal("unit_price"),
                        resultSet.getString("currency"), resultSet.getString("recipient_name"),
                        resultSet.getString("postal_code"), resultSet.getString("address_line1"),
                        resultSet.getString("address_line2")))
                .build();
    }

    @Bean
    OrderImportItemWriter orderImportWriter(JdbcTemplate jdbcTemplate) {
        return new OrderImportItemWriter(jdbcTemplate);
    }

    @Bean
    Step orderImportStep(JobRepository jobRepository,
                         PlatformTransactionManager transactionManager,
                         JdbcCursorItemReader<StagedOrderRow> orderImportReader,
                         OrderImportItemWriter orderImportWriter) {
        return new StepBuilder("orderImportStep", jobRepository)
                .<StagedOrderRow, StagedOrderRow>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(orderImportReader)
                .writer(orderImportWriter)
                .build();
    }

    @Bean
    Job orderImportJob(JobRepository jobRepository, @Qualifier("orderImportStep") Step orderImportStep) {
        return new JobBuilder("orderImportJob", jobRepository).start(orderImportStep).build();
    }
}
