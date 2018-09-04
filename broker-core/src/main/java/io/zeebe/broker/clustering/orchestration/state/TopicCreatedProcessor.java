/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.orchestration.state;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.TopicIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class TopicCreatedProcessor implements TypedRecordProcessor<TopicRecord> {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  private static final String REJECTION_REASON = "Topic exists already";

  private final Predicate<DirectBuffer> topicAlreadyCreated;
  private final Consumer<DirectBuffer> notifyListeners;
  private final BiConsumer<Long, TopicRecord> updateTopicState;

  public TopicCreatedProcessor(
      final Predicate<DirectBuffer> topicAlreadyCreated,
      final Consumer<DirectBuffer> notifyListeners,
      final BiConsumer<Long, TopicRecord> updateTopicState) {
    this.topicAlreadyCreated = topicAlreadyCreated;
    this.notifyListeners = notifyListeners;
    this.updateTopicState = updateTopicState;
  }

  @Override
  public void processRecord(
      TypedRecord<TopicRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    final TopicRecord topicEvent = event.getValue();
    final DirectBuffer topicName = topicEvent.getName();

    if (topicAlreadyCreated.test(topicName)) {
      LOG.warn(
          "Rejecting topic create complete as topic {} was already created",
          BufferUtil.bufferAsString(topicName));

      streamWriter.writeRejection(event, RejectionType.NOT_APPLICABLE, REJECTION_REASON);
    } else {
      sideEffect.accept(
          () -> {
            notifyListeners.accept(topicName);
            return true;
          });

      streamWriter.writeFollowUpEvent(event.getKey(), TopicIntent.CREATED, event.getValue());
      updateTopicState.accept(event.getKey(), event.getValue());
    }
  }
}
