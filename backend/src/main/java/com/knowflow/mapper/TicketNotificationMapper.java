package com.knowflow.mapper;

import com.knowflow.entity.TicketNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketNotificationMapper {

    int insert(TicketNotification notification);

    int countByEventId(@Param("eventId") String eventId);

    TicketNotification findByEventId(@Param("eventId") String eventId);

    long countByCustomerUserId(@Param("customerUserId") Long customerUserId);

    List<TicketNotification> findPageByCustomerUserId(
            @Param("customerUserId") Long customerUserId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
