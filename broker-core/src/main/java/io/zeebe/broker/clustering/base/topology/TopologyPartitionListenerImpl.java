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
package io.zeebe.broker.clustering.base.topology;

import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.channel.ActorConditions;
import org.agrona.collections.Int2ObjectHashMap;

public class TopologyPartitionListenerImpl implements TopologyPartitionListener {

  private final Int2ObjectHashMap<NodeInfo> partitionLeaders = new Int2ObjectHashMap<>();
  private volatile Integer systemPartitionLeaderId;
  private final ActorControl actor;
  private final ActorConditions conditions = new ActorConditions();

  public TopologyPartitionListenerImpl(final ActorControl actor) {
    this.actor = actor;
  }

  @Override
  public void onPartitionUpdated(final PartitionInfo partitionInfo, final NodeInfo member) {
    if (member.getLeaders().contains(partitionInfo)) {
      actor.submit(
          () -> {
            updatePartitionLeader(partitionInfo, member);
            conditions.signalConsumers();
          });
    }
  }

  private void updateSystemPartitionLeader(final NodeInfo member) {
    final Integer currentLeader = systemPartitionLeaderId;

    if (currentLeader == null || !currentLeader.equals(member.getNodeId())) {
      systemPartitionLeaderId = member.getNodeId();
    }
  }

  private void updatePartitionLeader(final PartitionInfo partitionInfo, final NodeInfo member) {
    final NodeInfo currentLeader = partitionLeaders.get(partitionInfo.getPartitionId());

    if (currentLeader == null || !currentLeader.equals(member)) {
      partitionLeaders.put(partitionInfo.getPartitionId(), member);
    }
  }

  public Int2ObjectHashMap<NodeInfo> getPartitionLeaders() {
    return partitionLeaders;
  }

  public Integer getSystemPartitionLeaderId() {
    return systemPartitionLeaderId;
  }

  public void addCondition(final ActorCondition condition) {
    conditions.registerConsumer(condition);
  }

  public void removeCondition(final ActorCondition condition) {
    conditions.removeConsumer(condition);
  }
}
