/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.exporter.api.record.Assertions.assertThat;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.record.RecordingExporter.messageStartEventSubscriptionRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class MessageStartEventTest {

  private static final String MESSAGE_NAME1 = "startMessage1";
  private static final String EVENT_ID1 = "startEventId1";

  private static final String MESSAGE_NAME2 = "startMessage2";
  private static final String EVENT_ID2 = "startEventId2";

  @Rule public EngineRule engine = new EngineRule();

  @Test
  public void shouldCorrelateMessageToStartEvent() {
    // given
    final Record<DeploymentRecordValue> deploymentRecord =
        engine.deploy(createWorkflowWithOneMessageStartEvent());
    final long workflowKey = getFirstDeployedWorkflowKey(deploymentRecord);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_OCCURRED).getFirst();

    assertThat(record.getValue()).hasWorkflowKey(workflowKey).hasElementId(EVENT_ID1);
  }

  @Test
  public void shouldCreateInstanceOnMessage() {
    // given
    final Record<DeploymentRecordValue> deploymentRecord =
        engine.deploy(createWorkflowWithOneMessageStartEvent());
    final long workflowKey = getFirstDeployedWorkflowKey(deploymentRecord);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords().limit(5).asList();

    assertThat(records)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            WorkflowInstanceIntent.EVENT_OCCURRED, // message
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // workflow instance
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // start event
            WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    assertThat(records.get(3).getValue()).hasElementId(EVENT_ID1);
  }

  @Test
  public void shouldMergeMessageVariables() {
    // given
    engine.deploy(createWorkflowWithOneMessageStartEvent());

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    assertThat(RecordingExporter.variableRecords().withName("foo").withValue("\"bar\"").exists())
        .isTrue();
  }

  @Test
  public void shouldApplyOutputMappingsOfMessageStartEvent() {
    // given
    engine.deploy(createWorkflowWithMessageStartEventOutputMapping());

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    assertThat(
            RecordingExporter.variableRecords().withName("mappedfoo").withValue("\"bar\"").exists())
        .isTrue();
  }

  @Test
  public void shouldCreateInstancesForMultipleMessagesOfSameName() {
    // given
    final Record<DeploymentRecordValue> record =
        engine.deploy(createWorkflowWithOneMessageStartEvent());

    final long workflowKey = getFirstDeployedWorkflowKey(record);

    // wait until subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engine.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));
    engine.publishMessage(MESSAGE_NAME1, "order-124", asMsgPack("foo", "bar"));

    // then

    // check if two instances are created
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .limit(2)
            .asList();

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    final WorkflowInstanceRecordValue recordValue1 = records.get(0).getValue();
    final WorkflowInstanceRecordValue recordValue2 = records.get(1).getValue();

    assertThat(recordValue1.getWorkflowInstanceKey())
        .isNotEqualTo(recordValue2.getWorkflowInstanceKey());
  }

  @Test
  public void shouldCreateInstancesForDifferentMessages() {
    // given
    final Record<DeploymentRecordValue> record =
        engine.deploy(createWorkflowWithTwoMessageStartEvent());

    final long workflowKey = getFirstDeployedWorkflowKey(record);

    // check if two subscriptions are opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));
    engine.publishMessage(MESSAGE_NAME2, "order-124", asMsgPack("foo", "bar"));

    // then

    // check if two instances are created
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .withElementType(BpmnElementType.START_EVENT)
            .limit(2)
            .asList();

    assertThat(records.size()).isEqualTo(2);
    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey);

    assertThat(records.get(0).getValue())
        .hasElementId(EVENT_ID1); // Message 1 triggers start event 1
    assertThat(records.get(1).getValue())
        .hasElementId(EVENT_ID2); // Message 2 triggers start event 2
  }

  @Test
  public void shouldNotCreateInstanceOfOldVersion() {
    // given
    engine.deploy(createWorkflowWithOneMessageStartEvent());

    // new version
    final Record<DeploymentRecordValue> record =
        engine.deploy(createWorkflowWithOneMessageStartEvent());
    final long workflowKey2 = getFirstDeployedWorkflowKey(record);

    // wait until second subscription is opened
    assertThat(
            messageStartEventSubscriptionRecords(MessageStartEventSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.publishMessage(MESSAGE_NAME1, "order-123", asMsgPack("foo", "bar"));

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords().limit(5).asList();

    assertThat(records.stream().map(r -> r.getMetadata().getIntent()))
        .containsExactly(
            WorkflowInstanceIntent.EVENT_OCCURRED, // message
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // workflow instance
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING, // start event
            WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(records).allMatch(r -> r.getValue().getWorkflowKey() == workflowKey2);
  }

  private static BpmnModelInstance createWorkflowWithOneMessageStartEvent() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createWorkflowWithTwoMessageStartEvent() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("processId");
    process.startEvent(EVENT_ID1).message(m -> m.name(MESSAGE_NAME1).id("startmsgId1")).endEvent();
    process.startEvent(EVENT_ID2).message(m -> m.name(MESSAGE_NAME2).id("startmsgId2")).endEvent();

    return process.done();
  }

  private static BpmnModelInstance createWorkflowWithMessageStartEventOutputMapping() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .zeebeOutput("foo", "mappedfoo")
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  @SuppressWarnings("unchecked")
  private long getFirstDeployedWorkflowKey(final Record<DeploymentRecordValue> deploymentRecord) {
    return deploymentRecord.getValue().getDeployedWorkflows().get(0).getWorkflowKey();
  }
}
