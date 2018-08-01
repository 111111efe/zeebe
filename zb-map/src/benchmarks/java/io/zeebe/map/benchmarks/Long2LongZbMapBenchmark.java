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
package io.zeebe.map.benchmarks;

import io.zeebe.map.Long2LongZbMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import java.util.HashMap;
import net.openhft.chronicle.map.ChronicleMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;

@BenchmarkMode(Mode.Throughput)
public class Long2LongZbMapBenchmark
{

    @Benchmark
    @Threads(1)
    public long removeLinearKeysChronicleMap(Long2LongChronicleMapSupplier chronicleMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final ChronicleMap<Long, Long> map = chronicleMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.remove(keys[i]);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long randomKeysChronicleMap(Long2LongChronicleMapSupplier chronicleMapSupplier, RandomKeysSupplier randomKeysSupplier)
    {
        final ChronicleMap<Long, Long> map = chronicleMapSupplier.map;
        final long[] keys = randomKeysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i]);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long linearKeysChronicleMap(Long2LongChronicleMapSupplier chronicleMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final ChronicleMap<Long, Long> map = chronicleMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i]);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long randomKeysFastUtilMap(Long2LongFastUtilMapSupplier fastUtilMapSupplier, RandomKeysSupplier randomKeysSupplier)
    {
        final Long2LongMap map = fastUtilMapSupplier.map;
        final long[] keys = randomKeysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i]);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long linearKeysFastUtilMap(Long2LongFastUtilMapSupplier fastUtilMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final Long2LongMap map = fastUtilMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i]);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long removeLinearKeysFastUtilMap(Long2LongFastUtilMapSupplier fastUtilMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final Long2LongMap map = fastUtilMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            map.remove(keys[i], -1);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long randomKeys(Long2LongZbMapSupplier hashMapSupplier, RandomKeysSupplier randomKeysSupplier)
    {
        final Long2LongZbMap map = hashMapSupplier.map;
        final long[] keys = randomKeysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i], -1);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long linearKeys(Long2LongZbMapSupplier hashMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final Long2LongZbMap map = hashMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i], -1);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long removeLinearKeys(Long2LongZbMapSupplier hashMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final Long2LongZbMap map = hashMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.remove(keys[i], -1);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long baselineLinearKeys(Long2LongHashMapSupplier hashMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final HashMap<Long, Long> map = hashMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i]);
        }

        return result;
    }


    @Benchmark
    @Threads(1)
    public long baselineRandomKeys(Long2LongHashMapSupplier hashMapSupplier, RandomKeysSupplier keysSupplier)
    {
        final HashMap<Long, Long> map = hashMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.get(keys[i]);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long removeBaselineLinearKeys(Long2LongHashMapSupplier hashMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final HashMap<Long, Long> map = hashMapSupplier.map;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            map.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += map.remove(keys[i]);
        }

        return result;
    }

}
