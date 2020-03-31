/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.LogStorageReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;

class ListLogStrage implements LogStorage {

  private final List<ZeebeEntry> entries;
  private LongConsumer positionListener;

  public ListLogStrage() {
    entries = new CopyOnWriteArrayList<>();
  }

  public void setPositionListener(final LongConsumer positionListener) {
    this.positionListener = positionListener;
  }

  @Override
  public LogStorageReader newReader() {
    return new LogStorageReader() {
      @Override
      public boolean isEmpty() {
        return entries.isEmpty();
      }

      @Override
      public long read(final DirectBuffer readBuffer, final long address) {
        final var index = (int) (address - 1);

        if (index < 0 || index >= entries.size()) {
          return OP_RESULT_NO_DATA;
        }

        final var zeebeEntry = entries.get(index);
        final var data = zeebeEntry.data();
        readBuffer.wrap(data, data.position(), data.remaining());

        return address + 1;
      }

      @Override
      public long readLastBlock(final DirectBuffer readBuffer) {
        return read(readBuffer, entries.size());
      }

      @Override
      public long lookUpApproximateAddress(final long position) {

        if (position == Long.MIN_VALUE) {
          return entries.isEmpty() ? OP_RESULT_INVALID_ADDR : 1;
        }

        for (int idx = 0; idx < entries.size(); idx++) {
          final var zeebeEntry = entries.get(idx);
          if (zeebeEntry.lowestPosition() <= position && position <= zeebeEntry.highestPosition()) {
            return idx;
          }
        }

        return 1;
      }

      @Override
      public void close() {}
    };
  }

  @Override
  public void append(
      final long lowestPosition,
      final long highestPosition,
      final ByteBuffer blockBuffer,
      final AppendListener listener) {
    try {
      final var zeebeEntry =
          new ZeebeEntry(
              0, System.currentTimeMillis(), lowestPosition, highestPosition, blockBuffer);
      entries.add(zeebeEntry);
      listener.onWrite(entries.size());

      if (positionListener != null) {
        positionListener.accept(zeebeEntry.highestPosition());
      }
      listener.onCommit(entries.size());
    } catch (final Exception e) {
      listener.onWriteError(e);
    }
  }

  @Override
  public void open() throws IOException {}

  @Override
  public void close() {
    entries.clear();
  }

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public void flush() throws Exception {}
}
