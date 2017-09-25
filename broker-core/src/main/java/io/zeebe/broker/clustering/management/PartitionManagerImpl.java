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
package io.zeebe.broker.clustering.management;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerListIterator;
import io.zeebe.broker.clustering.gossip.data.RaftMembership;
import io.zeebe.broker.clustering.management.message.CreatePartitionMessage;
import io.zeebe.broker.clustering.member.Member;
import io.zeebe.clustering.gossip.RaftMembershipState;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.TransportMessage;
import io.zeebe.util.buffer.BufferUtil;

public class PartitionManagerImpl implements PartitionManager
{

    protected final PeerList peerList;
    private final CreatePartitionMessage messageWriter = new CreatePartitionMessage();
    protected final TransportMessage message = new TransportMessage();
    protected final ClientTransport transport;

    protected final MemberIterator memberIterator = new MemberIterator();

    public PartitionManagerImpl(PeerList peerList, ClientTransport transport)
    {
        this.peerList = peerList;
        this.transport = transport;
    }

    @Override
    public boolean createPartitionRemote(SocketAddress remote, DirectBuffer topicName, int partitionId)
    {
        final DirectBuffer nameBuffer = BufferUtil.cloneBuffer(topicName);

        messageWriter
            .partitionId(partitionId)
            .topicName(nameBuffer);

        final RemoteAddress remoteAddress = transport.registerRemoteAddress(remote);

        message.writer(messageWriter)
            .remoteAddress(remoteAddress);

        Loggers.SYSTEM_LOGGER.info("Creating partition {}/{} at {}", BufferUtil.bufferAsString(topicName), partitionId, remote);

        return transport.getOutput().sendMessage(message);
    }

    /*
     * There are some issues with how this connects the gossip state with the system partition processing.
     *
     * * not garbage-free
     * * not thread-safe (peer list is shared state between multiple actors and therefore threads)
     * * not efficient (the stream processor iterates all partitions when it looks for a specific
     *   partition's leader)
     *
     * This code can be refactored in any way when we rewrite gossip.
     * As a baseline, the system stream processor needs to know for a set of partitions
     * if a partition leader becomes known. In that case, it must generate a command on the system log.
     */
    @Override
    public Iterator<Member> getKnownMembers()
    {
        final PeerList copy = peerList.copy();
        memberIterator.wrap(copy);
        return memberIterator;
    }

    protected static class PartitionIterator implements Iterator<Partition>
    {
        protected PartitionImpl partition1 = new PartitionImpl();
        protected PartitionImpl partition2 = new PartitionImpl();
        protected PartitionImpl nextPartition = null;

        protected Iterator<Peer> peerIterator;
        protected Iterator<RaftMembership> raftMemberIterator;
        protected Peer currentPeer;

        public void wrap(Iterator<RaftMembership> raftMemberIterator)
        {
            this.raftMemberIterator = raftMemberIterator;
            this.currentPeer = null;
            seekNextPartitionLeader();
        }

        @Override
        public boolean hasNext()
        {
            return nextPartition != null;
        }

        protected void seekNextPartitionLeader()
        {
            while (raftMemberIterator.hasNext())
            {
                final RaftMembership membership = raftMemberIterator.next();

                if (membership.state() == RaftMembershipState.LEADER)
                {
                    nextPartition = nextPartition == partition1 ? partition2 : partition1;
                    nextPartition.wrap(
                            membership.topicNameBuffer(),
                            0,
                            membership.topicNameLength(),
                            membership.partitionId());

                    return;
                }
            }

            nextPartition = null;
        }

        @Override
        public Partition next()
        {
            final Partition partitionToReturn = nextPartition;
            seekNextPartitionLeader();
            return partitionToReturn;
        }
    }

    protected static class PartitionImpl implements Partition
    {

        protected UnsafeBuffer topicName = new UnsafeBuffer(0, 0);
        protected int partitionId;

        public void wrap(DirectBuffer topicName, int offset, int length, int partitionId)
        {
            this.topicName.wrap(topicName, offset, length);
            this.partitionId = partitionId;
        }

        @Override
        public DirectBuffer getTopicName()
        {
            return topicName;
        }

        @Override
        public int getPartitionId()
        {
            return partitionId;
        }

    }

    protected static class MemberIterator implements Iterator<Member>
    {
        protected PeerListIterator peerListIt;
        protected MemberImpl currentMember = new MemberImpl();

        public void wrap(PeerList peerList)
        {
            this.peerListIt = peerList.iterator();
        }

        @Override
        public boolean hasNext()
        {
            return peerListIt.hasNext();
        }

        @Override
        public Member next()
        {
            currentMember.wrap(peerListIt.next());
            return currentMember;
        }
    }

    protected static class MemberImpl implements Member
    {
        protected SocketAddress socketAddress;
        protected PartitionIterator partitionIterator = new PartitionIterator();


        public void wrap(Peer peer)
        {
            this.socketAddress = peer.managementEndpoint();
            this.partitionIterator.wrap(peer.raftMemberships().iterator());
        }


        @Override
        public SocketAddress getManagementAddress()
        {
            return socketAddress;
        }


        @Override
        public Iterator<Partition> getLeadingPartitions()
        {
            return partitionIterator;
        }
    }

}
