/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.test.matchers.PredicateMatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Tests {@link AggregateTestFixture} in polymorphic scenarios.
 *
 * @author Milan Savic
 */
class FixtureTest_Polymorphism {

    private FixtureConfiguration<AggregateA> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(AggregateA.class).registerSubtype(AggregateB.class)
                                                              .registerSubtype(AggregateC.class);
    }

    private static Stream<Arguments> provideForCreationalTest() {
        return Stream.of(
                Arguments.of((Function<String, Object>) CreateBCommand::new, "AggregateB"),
                Arguments.of((Function<String, Object>) CreateCCommand::new, "AggregateC")
        );
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("provideForCreationalTest")
    void testCreationOfAggregate(Function<String, Object> commandBuilder, String aggregateType) {
        String id = "id";
        fixture.givenNoPriorActivity()
               .when(commandBuilder.apply(id))
               .expectEventsMatching(new PredicateMatcher<>(events -> {
                   DomainEventMessage<CreatedEvent> evt = (DomainEventMessage<CreatedEvent>) events.get(0);
                   return evt.getType().equals(aggregateType)
                           && events.size() == 1
                           && evt.getPayload().id.equals(id);
               }));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AggregateB", "AggregateC"})
    void testCommonCommandOnAggregate(String aggregateType) {
        String id = "id";
        DomainEventMessage<CreatedEvent> creationalEvent = new GenericDomainEventMessage<>(aggregateType,
                                                                                           id,
                                                                                           0,
                                                                                           new CreatedEvent(id));
        fixture.given(creationalEvent)
               .when(new CommonCommand(id))
               .expectResultMessagePayload(aggregateType + id);
    }

    private static class CreateBCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CreateBCommand(String id) {
            this.id = id;
        }
    }

    private static class CreateCCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CreateCCommand(String id) {
            this.id = id;
        }
    }

    private static class CreatedEvent {

        private final String id;

        private CreatedEvent(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CreatedEvent that = (CreatedEvent) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class CommonCommand {

        @TargetAggregateIdentifier
        private final String id;

        private CommonCommand(String id) {
            this.id = id;
        }
    }

    private static abstract class AggregateA {

        @AggregateIdentifier
        private String id;

        @EventSourcingHandler
        public void on(CreatedEvent evt) {
            this.id = evt.id;
        }

        @CommandHandler
        public String handle(CommonCommand cmd) {
            return this.getClass().getSimpleName() + cmd.id;
        }
    }

    private static class AggregateB extends AggregateA {

        public AggregateB() {
        }

        @CommandHandler
        public AggregateB(CreateBCommand cmd) {
            apply(new CreatedEvent(cmd.id));
        }
    }

    private static class AggregateC extends AggregateA {

        public AggregateC() {
        }

        @CommandHandler
        public AggregateC(CreateCCommand cmd) {
            apply(new CreatedEvent(cmd.id));
        }
    }
}
