package com.fasterxml.uuid.impl;

import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.UUIDClock;
import com.fasterxml.uuid.UUIDType;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of UUID generator that uses time/location based generation
 * method field from the Unix Epoch timestamp source - the number of 
 * milliseconds seconds since midnight 1 Jan 1970 UTC, leap seconds excluded.
 * This is usually referred to as "Version 7".
 * In addition to that random part is regenerated for every new UUID.
 * This removes possibilities to have almost similar UUID, when calls
 * to generate are made within same millisecond.
 * <p>
 * As all JUG provided implementations, this generator is fully thread-safe.
 * Additionally it can also be made externally synchronized with other instances
 * (even ones running on other JVMs); to do this, use
 * {@link com.fasterxml.uuid.ext.FileBasedTimestampSynchronizer} (or
 * equivalent).
 *
 * @since 4.1
 */
public class TimeBasedEpochRandomGenerator extends NoArgGenerator
{
    private static final int ENTROPY_BYTE_LENGTH = 10;

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    /**
     * Random number generator that this generator uses.
     */
    protected final Random _random;

    /**
     * Underlying {@link UUIDClock} used for accessing current time, to use for
     * generation.
     *
     * @since 4.3
     */
    protected final UUIDClock _clock;

    private final byte[] _lastEntropy  = new byte[ENTROPY_BYTE_LENGTH];
    private final Lock lock = new ReentrantLock();

    /*
    /**********************************************************************
    /* Construction
    /**********************************************************************
     */

    /**
     * @param rnd Random number generator to use for generating UUIDs; if null,
     *   shared default generator is used. Note that it is strongly recommend to
     *   use a <b>good</b> (pseudo) random number generator; for example, JDK's
     *   {@link SecureRandom}.
     */
    public TimeBasedEpochRandomGenerator(Random rnd) {
        this(rnd, UUIDClock.systemTimeClock());
    }

    /**
     * @param rnd Random number generator to use for generating UUIDs; if null,
     *   shared default generator is used. Note that it is strongly recommend to
     *   use a <b>good</b> (pseudo) random number generator; for example, JDK's
     *   {@link SecureRandom}.
     * @param clock clock Object used for accessing current time to use for generation
     */
    public TimeBasedEpochRandomGenerator(Random rnd, UUIDClock clock)
    {
        if (rnd == null) {
            rnd = LazyRandom.sharedSecureRandom(); 
        }
        _random = rnd;
        _clock = clock;
    }

    /*
    /**********************************************************************
    /* Access to config
    /**********************************************************************
     */

    @Override
    public UUIDType getType() { return UUIDType.TIME_BASED_EPOCH; }

    /*
    /**********************************************************************
    /* UUID generation
    /**********************************************************************
     */

    @Override
    public UUID generate()
    {
        return construct(_clock.currentTimeMillis());
    }

    /**
     * Method that will construct actual {@link UUID} instance for given
     * unix epoch timestamp: called by {@link #generate()} but may alternatively be
     * called directly to construct an instance with known timestamp.
     * NOTE: calling this method directly produces somewhat distinct UUIDs as
     * "entropy" value is still generated as necessary to avoid producing same
     * {@link UUID} even if same timestamp is being passed.
     *
     * @param rawTimestamp unix epoch millis
     *
     * @return unix epoch time based UUID
     *
     * @since 4.3
     */
    public UUID construct(long rawTimestamp)
    {
        lock.lock();
        try { 
            _random.nextBytes(_lastEntropy);
            return UUIDUtil.constructUUID(UUIDType.TIME_BASED_EPOCH, (rawTimestamp << 16) | _toShort(_lastEntropy, 0), _toLong(_lastEntropy, 2));
        } finally {
            lock.unlock();
        }
    }

    /*
    /**********************************************************************
    /* Internal methods
    /**********************************************************************
     */

    protected final static long _toLong(byte[] buffer, int offset)
    {
        long l1 = _toInt(buffer, offset);
        long l2 = _toInt(buffer, offset+4);
        long l = (l1 << 32) + ((l2 << 32) >>> 32);
        return l;
    }

    private final static long _toInt(byte[] buffer, int offset)
    {
        return (buffer[offset] << 24)
            + ((buffer[++offset] & 0xFF) << 16)
            + ((buffer[++offset] & 0xFF) << 8)
            + (buffer[++offset] & 0xFF);
    }

    private final static long _toShort(byte[] buffer, int offset)
    {
        return ((buffer[offset] & 0xFF) << 8)
            + (buffer[++offset] & 0xFF);
    }
}
