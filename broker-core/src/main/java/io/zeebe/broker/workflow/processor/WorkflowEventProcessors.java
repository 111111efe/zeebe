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

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.processor.instance.CreateWorkflowInstanceProcessor;
import io.zeebe.broker.workflow.processor.message.CloseWorkflowInstanceSubscription;
import io.zeebe.broker.workflow.processor.message.CorrelateWorkflowInstanceSubscription;
import io.zeebe.broker.workflow.processor.message.OpenWorkflowInstanceSubscriptionProcessor;
import io.zeebe.broker.workflow.processor.timer.CancelTimerProcessor;
import io.zeebe.broker.workflow.processor.timer.CreateTimerProcessor;
import io.zeebe.broker.workflow.processor.timer.DueDateTimerChecker;
import io.zeebe.broker.workflow.processor.timer.TriggerTimerProcessor;
import io.zeebe.broker.workflow.processor.variable.UpdateVariableDocumentProcessor;
import io.zeebe.broker.workflow.state.ElementInstanceState;
import io.zeebe.broker.workflow.state.VariablesState;
import io.zeebe.broker.workflow.state.WorkflowEngineState;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import java.util.Arrays;
import java.util.List;

public class WorkflowEventProcessors {

  private static final List<WorkflowInstanceIntent> WORKFLOW_INSTANCE_COMMANDS =
      Arrays.asList(WorkflowInstanceIntent.CANCEL);

  private static boolean isWorkflowInstanceEvent(WorkflowInstanceIntent intent) {
    return !WORKFLOW_INSTANCE_COMMANDS.contains(intent);
  }

  public static BpmnStepProcessor addWorkflowProcessors(
      TypedEventStreamProcessorBuilder typedProcessorBuilder,
      ZeebeState zeebeState,
      SubscriptionCommandSender subscriptionCommandSender,
      TopologyManager topologyManager,
      DueDateTimerChecker timerChecker,
      int partitionsCount) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final WorkflowInstanceSubscriptionState subscriptionState =
        zeebeState.getWorkflowInstanceSubscriptionState();

    final WorkflowEngineState workflowEngineState = new WorkflowEngineState(workflowState);
    typedProcessorBuilder.withListener(workflowEngineState);

    addWorkflowInstanceCommandProcessor(typedProcessorBuilder, workflowEngineState, zeebeState);

    final CatchEventBehavior catchEventOutput =
        new CatchEventBehavior(zeebeState, subscriptionCommandSender, partitionsCount);
    final BpmnStepProcessor bpmnStepProcessor =
        new BpmnStepProcessor(workflowEngineState, zeebeState, catchEventOutput);
    addBpmnStepProcessor(typedProcessorBuilder, bpmnStepProcessor);

    addMessageStreamProcessors(
        typedProcessorBuilder,
        subscriptionState,
        topologyManager,
        subscriptionCommandSender,
        zeebeState);
    addTimerStreamProcessors(typedProcessorBuilder, timerChecker, zeebeState, catchEventOutput);
    addVariableDocumentStreamProcessors(typedProcessorBuilder, zeebeState);
    addWorkflowInstanceCreationStreamProcessors(typedProcessorBuilder, zeebeState);

    return bpmnStepProcessor;
  }

  private static void addWorkflowInstanceCommandProcessor(
      final TypedEventStreamProcessorBuilder builder,
      WorkflowEngineState workflowEngineState,
      final ZeebeState zeebeState) {

    final WorkflowInstanceCommandProcessor commandProcessor =
        new WorkflowInstanceCommandProcessor(workflowEngineState, zeebeState.getKeyGenerator());

    WORKFLOW_INSTANCE_COMMANDS.forEach(
        intent -> builder.onCommand(ValueType.WORKFLOW_INSTANCE, intent, commandProcessor));
  }

  private static void addBpmnStepProcessor(
      TypedEventStreamProcessorBuilder streamProcessorBuilder,
      BpmnStepProcessor bpmnStepProcessor) {

    Arrays.stream(WorkflowInstanceIntent.values())
        .filter(WorkflowEventProcessors::isWorkflowInstanceEvent)
        .forEach(
            intent ->
                streamProcessorBuilder.onEvent(
                    ValueType.WORKFLOW_INSTANCE, intent, bpmnStepProcessor));
  }

  private static void addMessageStreamProcessors(
      final TypedEventStreamProcessorBuilder streamProcessorBuilder,
      WorkflowInstanceSubscriptionState subscriptionState,
      TopologyManager topologyManager,
      SubscriptionCommandSender subscriptionCommandSender,
      ZeebeState zeebeState) {
    streamProcessorBuilder
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.OPEN,
            new OpenWorkflowInstanceSubscriptionProcessor(subscriptionState))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CORRELATE,
            new CorrelateWorkflowInstanceSubscription(
                topologyManager, subscriptionState, subscriptionCommandSender, zeebeState))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CLOSE,
            new CloseWorkflowInstanceSubscription(subscriptionState));
  }

  private static void addTimerStreamProcessors(
      final TypedEventStreamProcessorBuilder streamProcessorBuilder,
      DueDateTimerChecker timerChecker,
      ZeebeState zeebeState,
      CatchEventBehavior catchEventOutput) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();

    streamProcessorBuilder
        .onCommand(
            ValueType.TIMER, TimerIntent.CREATE, new CreateTimerProcessor(zeebeState, timerChecker))
        .onCommand(
            ValueType.TIMER,
            TimerIntent.TRIGGER,
            new TriggerTimerProcessor(zeebeState, catchEventOutput))
        .onCommand(ValueType.TIMER, TimerIntent.CANCEL, new CancelTimerProcessor(workflowState))
        .withListener(timerChecker);
  }

  private static void addVariableDocumentStreamProcessors(
      TypedEventStreamProcessorBuilder builder, ZeebeState zeebeState) {
    final ElementInstanceState elementInstanceState =
        zeebeState.getWorkflowState().getElementInstanceState();
    final VariablesState variablesState = elementInstanceState.getVariablesState();

    builder.onCommand(
        ValueType.VARIABLE_DOCUMENT,
        VariableDocumentIntent.UPDATE,
        new UpdateVariableDocumentProcessor(elementInstanceState, variablesState));
  }

  private static void addWorkflowInstanceCreationStreamProcessors(
      TypedEventStreamProcessorBuilder builder, ZeebeState zeebeState) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final ElementInstanceState elementInstanceState = workflowState.getElementInstanceState();
    final VariablesState variablesState = elementInstanceState.getVariablesState();
    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();

    builder.onCommand(
        ValueType.WORKFLOW_INSTANCE_CREATION,
        WorkflowInstanceCreationIntent.CREATE,
        new CreateWorkflowInstanceProcessor(
            workflowState, elementInstanceState, variablesState, keyGenerator));
  }
}
