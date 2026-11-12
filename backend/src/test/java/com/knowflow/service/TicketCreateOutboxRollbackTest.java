package com.knowflow.service;

import com.knowflow.dto.CreateTicketRequest;
import com.knowflow.mapper.MessageOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ticket_outbox_tx;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql(scripts = "classpath:sql/ticket-outbox-schema.sql")
class TicketCreateOutboxRollbackTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MessageOutboxMapper messageOutboxMapper;

    @BeforeEach
    void seedCustomer() {
        jdbcTemplate.update("DELETE FROM ticket_operation_log");
        jdbcTemplate.update("DELETE FROM service_ticket");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password_hash, display_name, status, role) VALUES (1, 'c1', 'x', '客户', 'ACTIVE', 'CUSTOMER')"
        );
    }

    @Test
    void ticketAndOutboxShareTransactionAndRollBackTogether() {
        when(messageOutboxMapper.insert(any())).thenThrow(new IllegalStateException("outbox write failed"));

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("打印机无法出纸");
        request.setDescription("卡纸后无法继续打印");
        request.setCategory("HARDWARE");
        request.setPriority("HIGH");

        assertThrows(IllegalStateException.class, () -> ticketService.create(1L, request));
        Integer tickets = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM service_ticket", Integer.class);
        Integer logs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_operation_log", Integer.class);
        assertEquals(0, tickets);
        assertEquals(0, logs);
    }
}
