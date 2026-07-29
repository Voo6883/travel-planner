package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.Conversation;
import com.travelplanner.infrastructure.persistence.entity.ConversationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * {@link ConversationEntity} ↔ {@link Conversation}.
 *
 * <p>{@code nextMessageSeq} is copied in both directions and that is load-bearing. It is the
 * sequence allocator: dropping it from the write mapping would reset a live conversation's counter
 * to 1 on the next save and make every subsequent append collide with
 * {@code uq_message_conversation_seq}. {@code PersistenceMapperConfig}'s
 * {@code unmappedTargetPolicy = ERROR} is what stops that from being a silent omission.
 *
 * <p><strong>Why {@code archive} and {@code recordAppend} are ignored.</strong> MapStruct treats
 * any single-argument method returning the enclosing type as a fluent setter, so these two domain
 * transitions look to it like writable properties. They are not. The ignores are stated per
 * property rather than by relaxing {@code unmappedTargetPolicy} on the method, which would also
 * hide a column somebody genuinely forgot.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface ConversationPersistenceMapper {

    @Mapping(target = "archive", ignore = true)
    @Mapping(target = "recordAppend", ignore = true)
    Conversation toDomain(ConversationEntity entity);

    void applyToEntity(Conversation conversation, @MappingTarget ConversationEntity entity);
}
