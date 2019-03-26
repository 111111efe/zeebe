/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.logstreams.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.zeebe.db.DbContext;
import io.zeebe.db.TransactionOperation;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ProcessingStateMachineTest {

  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

  private ProcessingStateMachine processingStateMachine;

  @Mock private StreamProcessor streamProcessor;
  @Mock private LogStreamReader logStreamReader;
  @Mock private LogStreamRecordWriter logStreamWriter;
  @Mock private DbContext dbContext;
  @Mock private LogStream logStream;

  private ZeebeDbTransaction zeebeDbTransaction;
  private ActorControl actor;
  private EventProcessor eventProcessor;

  @Before
  public void setup() {
    initMocks(this);

    final ControllableActor controllableActor = new ControllableActor();
    actor = controllableActor.getActor();

    when(logStreamReader.hasNext()).thenReturn(true, false);
    when(logStreamReader.next()).thenReturn(mock(LoggedEvent.class));

    when(logStreamWriter.producerId(anyInt())).thenReturn(logStreamWriter);

    zeebeDbTransaction = mock(ZeebeDbTransaction.class);

    zeebeDbTransaction = spy(new Transaction());
    when(dbContext.getCurrentTransaction()).thenReturn(zeebeDbTransaction);
    when(logStream.getCommitPosition()).thenReturn(Long.MAX_VALUE);

    eventProcessor = mock(EventProcessor.class);

    when(eventProcessor.executeSideEffects()).thenReturn(true);
    when(eventProcessor.writeEvent(any())).thenReturn(1L);

    when(streamProcessor.onEvent(any())).thenReturn(eventProcessor);

    final StreamProcessorContext streamProcessorContext = new StreamProcessorContext();
    streamProcessorContext.setLogStream(logStream);
    streamProcessorContext.setActorControl(actor);
    streamProcessorContext.setLogStreamReader(logStreamReader);
    streamProcessorContext.setLogStreamWriter(logStreamWriter);
    streamProcessorContext.setName("testProcessor");

    processingStateMachine =
        ProcessingStateMachine.builder()
            .setStreamProcessorContext(streamProcessorContext)
            .setMetrics(mock(StreamProcessorMetrics.class))
            .setStreamProcessor(streamProcessor)
            .setDbContext(dbContext)
            .setShouldProcessNext(() -> true)
            .setAbortCondition(() -> false)
            .build();

    actorSchedulerRule.submitActor(controllableActor);
  }

  @Test
  public void shouldRunLifecycle() throws Exception {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, dbContext, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).processEvent();

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInProcess() throws Exception {
    // given
    final RuntimeException expected = new RuntimeException("expected");
    doThrow(expected).doCallRealMethod().when(zeebeDbTransaction).run(any());
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, dbContext, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).onError(expected);

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotContinueWhenCommitPositionIsSmallerThenErrorPosition() throws Exception {
    // given
    final RuntimeException expected = new RuntimeException("expected");
    doThrow(expected).doCallRealMethod().when(zeebeDbTransaction).run(any());
    final long errorEventPos = 1234L;
    when(eventProcessor.writeEvent(any())).thenReturn(errorEventPos);
    when(logStreamReader.hasNext()).thenReturn(true);
    when(logStream.getCommitPosition()).thenReturn(1L);

    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, dbContext, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).onError(expected);

    // write error event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldContinueWhenCommitPositionIsGreaterThenErrorPosition() throws Exception {
    // given
    final RuntimeException expected = new RuntimeException("expected");
    doThrow(expected).doCallRealMethod().when(zeebeDbTransaction).run(any());
    final long errorEventPos = 1234L;
    when(eventProcessor.writeEvent(any())).thenReturn(errorEventPos);
    when(logStreamReader.hasNext()).thenReturn(true, true, false);
    when(logStream.getCommitPosition()).thenReturn(errorEventPos + 1);

    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, dbContext, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).onError(expected);

    // write error event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();

    // == next iteration
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());

    // write error event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInWriteEvent() throws Exception {
    // given
    final RuntimeException expected = new RuntimeException("expected");
    doThrow(expected).doReturn(1L).when(eventProcessor).writeEvent(any());
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, dbContext, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).processEvent();

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).onError(expected);

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInUpdateState() throws Exception {
    // given
    final RuntimeException expected = new RuntimeException("expected");
    doThrow(expected).doNothing().when(zeebeDbTransaction).commit();
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              return true;
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, dbContext, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).processEvent();

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // on error
    inOrder.verify(zeebeDbTransaction, times(1)).rollback();
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).onError(expected);

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldRunLifecycleOnErrorInExecuteSideEffects() throws Exception {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    when(eventProcessor.executeSideEffects())
        .then(
            (invocationOnMock -> {
              latch.countDown();
              throw new RuntimeException("expected");
            }));

    // when
    actor.call(() -> processingStateMachine.readNextEvent());
    actorSchedulerRule.workUntilDone();

    // then
    latch.await();
    final InOrder inOrder =
        Mockito.inOrder(streamProcessor, eventProcessor, dbContext, zeebeDbTransaction);

    // process
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(dbContext, times(1)).getCurrentTransaction();
    inOrder.verify(zeebeDbTransaction, times(1)).run(any());
    inOrder.verify(eventProcessor, times(1)).processEvent();

    // write event
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    // update state
    inOrder.verify(zeebeDbTransaction, times(1)).commit();

    // execute side effects
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  private class ControllableActor extends Actor {

    public ActorControl getActor() {
      return actor;
    }
  }

  private class Transaction implements ZeebeDbTransaction {

    @Override
    public void run(TransactionOperation operations) {
      try {
        operations.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void commit() {}

    @Override
    public void rollback() {}
  }
}
