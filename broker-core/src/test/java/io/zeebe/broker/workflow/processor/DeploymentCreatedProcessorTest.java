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
package io.zeebe.broker.workflow.processor;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.util.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.processor.deployment.DeploymentCreatedProcessor;
import io.zeebe.broker.workflow.processor.deployment.DeploymentEventProcessors;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeploymentCreatedProcessorTest {

  @Rule
  public StreamProcessorRule rule = new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION + 1);

  private StreamProcessorControl streamProcessor;
  private WorkflowState workflowState;

  @Before
  public void setUp() {
    streamProcessor =
        rule.initStreamProcessor(
            (typedEventStreamProcessorBuilder, zeebeDb, dbContext) -> {
              final ZeebeState zeebeState = new ZeebeState(zeebeDb, dbContext);
              workflowState = zeebeState.getWorkflowState();

              DeploymentEventProcessors.addDeploymentCreateProcessor(
                  typedEventStreamProcessorBuilder, workflowState);
              typedEventStreamProcessorBuilder.onEvent(
                  ValueType.DEPLOYMENT,
                  DeploymentIntent.CREATED,
                  new DeploymentCreatedProcessor(workflowState, false));

              return typedEventStreamProcessorBuilder.build();
            });
  }

  @Test
  public void shouldNotFailIfCantFindPreviousVersion() {
    // given
    streamProcessor.start();

    // when
    writeMessageStartRecord(1, 2);

    // then
    waitUntil(() -> rule.events().onlyMessageStartEventSubscriptionRecords().exists());
    assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.OPEN)
                .exists())
        .isTrue();
  }

  private void writeMessageStartRecord(final long key, final int version) {
    writeMessageStartRecord("process", "process.bpmn", key, version);
  }

  private void writeMessageStartRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final DeploymentRecord msgRecord =
        createMessageStartDeploymentRecord(processId, resourceId, key, version);
    rule.writeCommand(-1, DeploymentIntent.CREATE, msgRecord);
  }

  private static DeploymentRecord createMessageStartDeploymentRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId).startEvent().message("msg").endEvent().done();
    return createDeploymentRecord(modelInstance, processId, resourceId, key, version);
  }

  private static DeploymentRecord createNoneStartDeploymentRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    return createDeploymentRecord(modelInstance, processId, resourceId, key, version);
  }

  private static DeploymentRecord createDeploymentRecord(
      final BpmnModelInstance modelInstance,
      final String processId,
      final String resourceId,
      final long key,
      final int version) {
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceId))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    deploymentRecord
        .workflows()
        .add()
        .setKey(key)
        .setBpmnProcessId(processId)
        .setResourceName(resourceId)
        .setVersion(version);

    return deploymentRecord;
  }
}
