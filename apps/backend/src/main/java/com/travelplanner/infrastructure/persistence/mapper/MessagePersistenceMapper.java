package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.Message;
import com.travelplanner.infrastructure.persistence.entity.MessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * {@link MessageEntity} ↔ {@link Message}.
 *
 * <p>Both directions: a streaming assistant message is written repeatedly as deltas arrive
 * (ADR 007), so the write mapping is not a one-shot insert helper.
 *
 * <p>Nothing is transformed on the way through. Every field is a plain copy, which is the property
 * that matters here — a mapper that derived {@code content} from anything would be a place where a
 * provider reasoning trace could enter the row without appearing in a domain factory.
 *
 * <p><strong>Why the three terminal transitions are ignored.</strong> MapStruct treats any
 * single-argument method returning the enclosing type as a fluent setter, so
 * {@link Message#complete}, {@link Message#interrupt} and {@link Message#fail} look to it like
 * writable properties. They are transitions, and marking them individually keeps
 * {@code PersistenceMapperConfig}'s ERROR policy in force for the columns that really are columns.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface MessagePersistenceMapper {

    @Mapping(target = "complete", ignore = true)
    @Mapping(target = "interrupt", ignore = true)
    @Mapping(target = "fail", ignore = true)
    Message toDomain(MessageEntity entity);

    void applyToEntity(Message message, @MappingTarget MessageEntity entity);
}
