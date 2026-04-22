package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {

    List<RestaurantOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<RestaurantOrder> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    List<RestaurantOrder> findByStatusInAndDeliveryPerson_IdOrderByCreatedAtAsc(Collection<String> statuses, UUID deliveryPersonId);

    List<RestaurantOrder> findByClient_IdAndStatusNotInOrderByCreatedAtDesc(UUID clientId, Collection<String> statuses);

    List<RestaurantOrder> findByClient_IdOrderByCreatedAtDesc(UUID clientId);
}
