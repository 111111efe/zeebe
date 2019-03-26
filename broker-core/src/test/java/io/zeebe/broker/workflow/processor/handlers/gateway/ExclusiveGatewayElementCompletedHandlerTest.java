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
package io.zeebe.broker.workflow.processor.handlers.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.workflow.model.element.ExecutableExclusiveGateway;
import io.zeebe.broker.workflow.processor.handlers.element.ElementHandlerTestCase;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.StoredRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ExclusiveGatewayElementCompletedHandlerTest
    extends ElementHandlerTestCase<ExecutableExclusiveGateway> {
  private ExclusiveGatewayElementCompletedHandler<ExecutableExclusiveGateway> handler;

  @Override
  public void setUp() {
    super.setUp();
    handler = new ExclusiveGatewayElementCompletedHandler<>();
  }

  @Test
  public void shouldPublishDeferredRecords() {
    // given
    final ElementInstance flowScope = newElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final int currentTokens = flowScope.getNumberOfActiveTokens();
    final ElementInstance instance =
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED, flowScope);
    final StoredRecord deferred = deferRecordOn(instance);

    // when
    handler.handleState(context);

    // then
    assertThat(flowScope.getNumberOfActiveTokens()).isEqualTo(currentTokens + 1);
    verifyRecordPublished(deferred, flowScope.getKey());
  }
}
