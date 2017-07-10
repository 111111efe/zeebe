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
package io.zeebe.broker.util.msgpack.property;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.value.PackedValue;

public class PackedProperty extends BaseProperty<PackedValue>
{
    public PackedProperty(String key)
    {
        super(key, new PackedValue());
    }

    public PackedProperty(String key, DirectBuffer defaultValue)
    {
        super(key, new PackedValue(), new PackedValue(defaultValue, 0, defaultValue.capacity()));
    }

    public DirectBuffer getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(DirectBuffer buffer, int offset, int length)
    {
        value.wrap(buffer, offset, length);
        this.isSet = true;
    }

}
