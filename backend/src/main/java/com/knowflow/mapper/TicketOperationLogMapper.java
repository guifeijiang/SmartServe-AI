package com.knowflow.mapper;

import com.knowflow.entity.TicketOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketOperationLogMapper {

    int insert(TicketOperationLog log);

    List<TicketOperationLog> findByTicketId(@Param("ticketId") Long ticketId);
}
