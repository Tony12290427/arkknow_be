package com.arknow.relation.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for the Outbox table.
 * <p>
 * An Outbox record is written in the same database transaction as the business
 * operation (e.g. following insert). Canal picks up the binlog change and
 * publishes it to Kafka for async consumption.
 */
@Mapper
public interface OutboxMapper {

    /**
     * Inserts an outbox event.
     *
     * @param id            snowflake ID for the outbox record
     * @param aggregateType the domain aggregate (e.g. "following")
     * @param aggregateId   the related aggregate's ID
     * @param type          event type (e.g. "FollowCreated")
     * @param payload       JSON-serialized event payload
     */
    void insert(@Param("id") long id, @Param("aggregateType") String aggregateType,
                @Param("aggregateId") long aggregateId, @Param("type") String type,
                @Param("payload") String payload);
}
