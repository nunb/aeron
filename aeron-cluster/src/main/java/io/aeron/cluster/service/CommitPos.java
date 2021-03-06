/*
 * Copyright 2014-2018 Real Logic Ltd.
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
package io.aeron.cluster.service;

import io.aeron.Aeron;
import io.aeron.Counter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.status.CountersReader;

import static io.aeron.Aeron.NULL_VALUE;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.concurrent.status.CountersReader.KEY_OFFSET;
import static org.agrona.concurrent.status.CountersReader.RECORD_ALLOCATED;
import static org.agrona.concurrent.status.CountersReader.TYPE_ID_OFFSET;

/**
 * Counter representing the commit position that can consumed by a state machine on a stream for the a leadership term.
 * <p>
 * Key layout as follows:
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                     Leadership Term ID                        |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |            Log Position at base of Leadership Term            |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 *  |                   Leadership Term Length                      |
 *  |                                                               |
 *  +---------------------------------------------------------------+
 * </pre>
 */
public class CommitPos
{
    /**
     * Type id of a commit position counter.
     */
    public static final int COMMIT_POSITION_TYPE_ID = 203;

    /**
     * Human readable name for the counter.
     */
    public static final String NAME = "commit-pos: leadershipTermId=";

    public static final int TERM_BASE_LOG_POSITION_OFFSET = 0;
    public static final int LEADERSHIP_TERM_ID_OFFSET = TERM_BASE_LOG_POSITION_OFFSET + SIZE_OF_LONG;
    public static final int LEADERSHIP_TERM_LENGTH_OFFSET = LEADERSHIP_TERM_ID_OFFSET + SIZE_OF_LONG;
    public static final int KEY_LENGTH = LEADERSHIP_TERM_LENGTH_OFFSET + SIZE_OF_INT;

    /**
     * Allocate a counter to represent the commit position on stream for the current leadership term.
     *
     * @param aeron                to allocate the counter.
     * @param tempBuffer           to use for building the key and label without allocation.
     * @param leadershipTermId     of the log at the beginning of the leadership term.
     * @param termBaseLogPosition  of the log at the beginning of the leadership term.
     * @param leadershipTermLength length in bytes of the leadership term for the log.
     * @return the {@link Counter} for the commit position.
     */
    public static Counter allocate(
        final Aeron aeron,
        final MutableDirectBuffer tempBuffer,
        final long leadershipTermId,
        final long termBaseLogPosition,
        final long leadershipTermLength)
    {
        tempBuffer.putLong(LEADERSHIP_TERM_ID_OFFSET, leadershipTermId);
        tempBuffer.putLong(TERM_BASE_LOG_POSITION_OFFSET, termBaseLogPosition);
        tempBuffer.putLong(LEADERSHIP_TERM_LENGTH_OFFSET, leadershipTermLength);

        int labelOffset = 0;
        labelOffset += tempBuffer.putStringWithoutLengthAscii(KEY_LENGTH + labelOffset, NAME);
        labelOffset += tempBuffer.putLongAscii(KEY_LENGTH + labelOffset, leadershipTermId);

        return aeron.addCounter(
            COMMIT_POSITION_TYPE_ID, tempBuffer, 0, KEY_LENGTH, tempBuffer, KEY_LENGTH, labelOffset);
    }

    /**
     * Get the leadership term id for the given commit position.
     *
     * @param counters  to search within.
     * @param counterId for the active commit position.
     * @return the leadership term id if found otherwise {@link Aeron#NULL_VALUE}.
     */
    public static long getLeadershipTermId(final CountersReader counters, final int counterId)
    {
        final DirectBuffer buffer = counters.metaDataBuffer();

        if (counters.getCounterState(counterId) == RECORD_ALLOCATED)
        {
            final int recordOffset = CountersReader.metaDataOffset(counterId);

            if (buffer.getInt(recordOffset + TYPE_ID_OFFSET) == COMMIT_POSITION_TYPE_ID)
            {
                return buffer.getLong(recordOffset + KEY_OFFSET + LEADERSHIP_TERM_ID_OFFSET);
            }
        }

        return NULL_VALUE;
    }

    /**
     * Get the accumulated log position as a base for this leadership term.
     *
     * @param counters  to search within.
     * @param counterId for the active commit position.
     * @return the base log position if found otherwise {@link Aeron#NULL_VALUE}.
     */
    public static long getTermBaseLogPosition(final CountersReader counters, final int counterId)
    {
        final DirectBuffer buffer = counters.metaDataBuffer();

        if (counters.getCounterState(counterId) == RECORD_ALLOCATED)
        {
            final int recordOffset = CountersReader.metaDataOffset(counterId);

            if (buffer.getInt(recordOffset + TYPE_ID_OFFSET) == COMMIT_POSITION_TYPE_ID)
            {
                return buffer.getLong(recordOffset + KEY_OFFSET + TERM_BASE_LOG_POSITION_OFFSET);
            }
        }

        return NULL_VALUE;
    }

    /**
     * Get the length in bytes for the leadership term.
     *
     * @param counters  to search within.
     * @param counterId for the active commit position.
     * @return the base log position if found otherwise {@link Aeron#NULL_VALUE}.
     */
    public static long getLeadershipTermLength(final CountersReader counters, final int counterId)
    {
        final DirectBuffer buffer = counters.metaDataBuffer();

        if (counters.getCounterState(counterId) == RECORD_ALLOCATED)
        {
            final int recordOffset = CountersReader.metaDataOffset(counterId);

            if (buffer.getInt(recordOffset + TYPE_ID_OFFSET) == COMMIT_POSITION_TYPE_ID)
            {
                return buffer.getLong(recordOffset + KEY_OFFSET + LEADERSHIP_TERM_LENGTH_OFFSET);
            }
        }

        return NULL_VALUE;
    }

    /**
     * Is the counter still active and log still recording?
     *
     * @param counters  to search within.
     * @param counterId to search for.
     * @return true if the counter is still active otherwise false.
     */
    public static boolean isActive(final CountersReader counters, final int counterId)
    {
        final DirectBuffer buffer = counters.metaDataBuffer();

        if (counters.getCounterState(counterId) == RECORD_ALLOCATED)
        {
            final int recordOffset = CountersReader.metaDataOffset(counterId);

            return buffer.getInt(recordOffset + TYPE_ID_OFFSET) == COMMIT_POSITION_TYPE_ID;
        }

        return false;
    }
}
