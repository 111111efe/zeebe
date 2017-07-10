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
package io.zeebe.broker.clustering.raft.util;

public class Quorum
{
    private int quorum;
    private int succeeded = 1;
    private int failed;
    private boolean complete;
    private boolean stepdown;

    public void open(final int quorum)
    {
        this.quorum = quorum;

        succeeded = 1;
        failed = 0;
        stepdown = false;
    }

    public void close()
    {
        complete = false;
    }

    protected void checkComplete()
    {
        if (!complete && (succeeded >= quorum || failed >= quorum))
        {
            complete = true;
        }
    }

    public Quorum succeed()
    {
        succeeded++;
        checkComplete();
        return this;
    }

    public Quorum fail()
    {
        failed++;
        checkComplete();
        return this;
    }

    public Quorum stepdown()
    {
        stepdown = true;
        complete = true;
        return this;
    }

    public boolean isCompleted()
    {
        return complete;
    }

    public boolean isElected()
    {
        return !stepdown && succeeded >= quorum;
    }

}
