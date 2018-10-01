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
package io.zeebe.broker.workflow.state;

import io.zeebe.broker.workflow.processor.WorkflowInstanceLifecycle;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ElementInstance implements Persistable {

  private final IndexedRecord elementRecord;

  private long parentKey = -1;
  private int childCount;
  private long jobKey;
  private int activeTokens = 0;

  ElementInstance() {
    this.elementRecord = new IndexedRecord();
  }

  public ElementInstance(
      long key,
      ElementInstance parent,
      WorkflowInstanceIntent state,
      WorkflowInstanceRecord value) {
    this.elementRecord = new IndexedRecord(key, state, value);
    parentKey = parent.getKey();
    parent.childCount++;
  }

  public ElementInstance(long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    this.elementRecord = new IndexedRecord(key, state, value);
  }

  public long getKey() {
    return elementRecord.getKey();
  }

  public WorkflowInstanceIntent getState() {
    return elementRecord.getState();
  }

  public void setState(WorkflowInstanceIntent state) {
    this.elementRecord.setState(state);
  }

  public WorkflowInstanceRecord getValue() {
    return elementRecord.getValue();
  }

  public void setValue(WorkflowInstanceRecord value) {
    this.elementRecord.setValue(value);
  }

  public long getJobKey() {
    return jobKey;
  }

  public void setJobKey(long jobKey) {
    this.jobKey = jobKey;
  }

  public void decrementChildCount() {
    childCount--;
  }

  public boolean canTerminate() {
    return WorkflowInstanceLifecycle.canTerminate(getState());
  }

  public void spawnToken() {
    this.activeTokens += 1;
  }

  public void consumeToken() {
    this.activeTokens -= 1;
  }

  public int getNumberOfActiveTokens() {
    return activeTokens;
  }

  public int getNumberOfActiveElementInstances() {
    return childCount;
  }

  public int getNumberOfActiveExecutionPaths() {
    return activeTokens + getNumberOfActiveElementInstances();
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    final int startOffset = offset;
    childCount = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Integer.BYTES;

    jobKey = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    activeTokens = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Integer.BYTES;

    parentKey = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    final int writtenLength = offset - startOffset;
    elementRecord.wrap(buffer, offset, length - writtenLength);
  }

  @Override
  public int getLength() {
    return 2 * Long.BYTES + 2 * Integer.BYTES + elementRecord.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    final int startOffset = offset;

    buffer.putInt(offset, childCount, ByteOrder.LITTLE_ENDIAN);
    offset += Integer.BYTES;

    buffer.putLong(offset, jobKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    buffer.putInt(offset, activeTokens, ByteOrder.LITTLE_ENDIAN);
    offset += Integer.BYTES;

    buffer.putLong(offset, parentKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    final int endLength = offset - startOffset;
    final int expectedLength = getLength() - elementRecord.getLength();
    assert endLength == expectedLength : "End length differs with getLength()";

    elementRecord.write(buffer, offset);
  }

  public void writeKey(MutableDirectBuffer keyBuffer, int offset) {
    int keyOffset = offset;
    keyBuffer.putLong(keyOffset, getKey());
    keyOffset += Long.BYTES;
    assert (keyOffset - offset) == getKeyLength()
        : "Offset problem: end length is not equal to expected key length";
  }

  public int getKeyLength() {
    return Long.BYTES;
  }

  public void writeParentKey(MutableDirectBuffer keyBuffer, int offset) {
    int keyOffset = offset;
    keyBuffer.putLong(keyOffset, parentKey);
    keyOffset += Long.BYTES;
    assert (keyOffset - offset) == getParentKeyLength()
        : "Offset problem: end length is not equal to expected key length";
  }

  public int getParentKeyLength() {
    return Long.BYTES;
  }

  public long getParentKey() {
    return parentKey;
  }
}
