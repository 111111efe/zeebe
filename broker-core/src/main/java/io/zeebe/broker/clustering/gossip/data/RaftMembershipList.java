/**
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
package io.zeebe.broker.clustering.gossip.data;

import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.clustering.gossip.GossipDecoder;
import io.zeebe.clustering.gossip.PeerDescriptorDecoder;
import io.zeebe.logstreams.log.LogStream;


public class RaftMembershipList implements Iterable<RaftMembership>
{

    public static final int MAX_RAFT_MEMBERS = 20;

    protected final RaftMembership[] elements = new RaftMembership[MAX_RAFT_MEMBERS];
    protected final RaftMembershipIterator iterator = new RaftMembershipIterator(this);

    protected int size = 0;

    public RaftMembershipList()
    {
        for (int i = 0; i < elements.length; i++)
        {
            elements[i] = new RaftMembership();
        }
    }

    public int size()
    {
        return size;
    }

    public int capacity()
    {
        return elements.length;
    }

    public RaftMembership get(final int index)
    {
        checkIndex(index);

        return elements[index];
    }

    public RaftMembershipList clear()
    {
        iterator.reset();

        size = 0;

        for (final RaftMembership value : elements)
        {
            value.reset();
        }

        return this;
    }

    @Override
    public Iterator<RaftMembership> iterator()
    {
        iterator.reset();

        return iterator;
    }

    public RaftMembershipList add(final RaftMembership raftMembership)
    {
        checkCapacity();

        elements[size]
            .reset()
            .partitionId(raftMembership.partitionId())
            .term(raftMembership.term())
            .state(raftMembership.state())
            .topicName(raftMembership.topicNameBuffer(), 0, raftMembership.topicNameLength());

        size++;

        return this;
    }

    public RaftMembershipList add(final Raft raft)
    {
        checkCapacity();

        elements[size]
            .reset()
            .reference(raft);

        size++;

        return this;
    }

    public RaftMembershipList add(final PeerDescriptorDecoder.RaftMembershipsDecoder decoder)
    {
        checkCapacity();

        final RaftMembership element = elements[size];

        element
            .reset()
            .partitionId(decoder.partitionId())
            .term(decoder.term())
            .state(decoder.state());

        final int topicNameLength = decoder.topicNameLength();
        element.topicNameLength(topicNameLength);

        decoder.getTopicName(element.topicNameMutableBuffer(), 0, topicNameLength);

        size++;

        return this;
    }

    public RaftMembershipList add(final GossipDecoder.PeersDecoder.RaftMembershipsDecoder decoder)
    {
        checkCapacity();

        final RaftMembership element = elements[size];

        element
            .reset()
            .partitionId(decoder.partitionId())
            .term(decoder.term())
            .state(decoder.state());

        final int topicNameLength = decoder.topicNameLength();
        element.topicNameLength(topicNameLength);

        decoder.getTopicName(element.topicNameMutableBuffer(), 0, topicNameLength);

        size++;

        return this;
    }

    public RaftMembershipList remove(final Raft raft)
    {
        final LogStream stream = raft.stream();
        final DirectBuffer topicName = stream.getTopicName();
        final int partitionId = stream.getPartitionId();

        for (int i = 0; i < size; i++)
        {
            final RaftMembership element = elements[i];

            if (topicName.equals(element.topicNameBuffer()) && partitionId == element.partitionId())
            {
                remove(i);
                break;
            }
        }

        return this;
    }

    private void remove(final int index)
    {
        checkIndex(index);

        size--;

        elements[index]
            .wrap(elements[size]);

        elements[size].reset();
    }

    private void checkIndex(final int index)
    {
        if (index < 0 || index >= size)
        {
            throw new IndexOutOfBoundsException(String.format(
                "Index: %d, Size: %d", index, size
            ));
        }
    }

    private void checkCapacity()
    {
        if (size() >= capacity())
        {
            throw new IllegalArgumentException(String.format(
                "Unable to add element to a full list of capacity %d", capacity()
            ));
        }
    }


    @Override
    public String toString()
    {
        return "RaftMembershipList{" +
            "size=" + size() +
            ", elements=" +
                StreamSupport.stream(this.spliterator(), false)
                    .map(RaftMembership::toString)
                    .collect(Collectors.joining(", ", "[", "]")) +
            '}';
    }

}
