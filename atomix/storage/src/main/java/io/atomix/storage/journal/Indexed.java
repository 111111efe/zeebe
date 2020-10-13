/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.storage.journal;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.Objects;

/** Indexed journal entry. */
public class Indexed<E> {
  private final long index;
  private final E entry;
  private final int size;

  public Indexed(final long index, final E entry, final int size) {
    this.index = index;
    this.entry = entry;
    this.size = size;
  }

  /**
   * Returns the entry index.
   *
   * @return The entry index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the indexed entry.
   *
   * @return The indexed entry.
   */
  public E entry() {
    return entry;
  }

  /**
   * Returns the serialized entry size.
   *
   * @return The serialized entry size.
   */
  public int size() {
    return size;
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, entry, size);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Indexed<?> indexed = (Indexed<?>) o;
    return index == indexed.index && size == indexed.size && Objects.equals(entry, indexed.entry);
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("index", index).add("entry", entry).toString();
  }
}
