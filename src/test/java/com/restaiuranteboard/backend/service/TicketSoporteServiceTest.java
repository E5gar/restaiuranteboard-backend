package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.TicketSoporteCounterRepository;
import com.restaiuranteboard.backend.repository.nosql.TicketSoporteRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketSoporteServiceTest {

    @Mock
    private TicketSoporteRepository ticketSoporteRepository;

    @Mock
    private TicketSoporteCounterRepository counterRepository;

    @Mock
    private RestaurantOrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private TicketSoporteService service;

    private User adminUser;

    @BeforeEach
    void setUp() {
        service = new TicketSoporteService(
                ticketSoporteRepository,
                counterRepository,
                orderRepository,
                userRepository,
                emailService,
                messagingTemplate
        );
        adminUser = TestEntities.user("admin@test.com", TestEntities.roleAdmin(), false);
    }

    @Test
    void crearTicket_throwsWhenUserNotCliente() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> service.crearTicket(
                adminUser.getId(),
                UUID.randomUUID().toString(),
                "PEDIDO_INCOMPLETO",
                "Descripcion del problema",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No autorizado.");

        verify(ticketSoporteRepository, never()).save(any());
    }

    @Test
    void crearTicket_throwsWhenOrderIdMissing() {
        User client = TestEntities.userCliente();
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.crearTicket(
                client.getId(),
                "",
                "PEDIDO_INCOMPLETO",
                "Descripcion del problema",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona un pedido.");
    }

    @Test
    void crearTicket_throwsWhenDescriptionBlank() {
        User client = TestEntities.userCliente();
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.crearTicket(
                client.getId(),
                UUID.randomUUID().toString(),
                "PEDIDO_INCOMPLETO",
                "   ",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Describe el problema.");
    }
}
