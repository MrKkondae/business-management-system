package com.bms.backend.global.persistence;

import java.math.BigInteger;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class MonotonicUlidGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_BYTES = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private long lastTimestamp = -1;
    private final byte[] lastRandom = new byte[RANDOM_BYTES];

    public synchronized String next() {
        long timestamp = System.currentTimeMillis();
        if (timestamp > lastTimestamp) {
            secureRandom.nextBytes(lastRandom);
            lastTimestamp = timestamp;
        } else {
            incrementRandom();
            timestamp = lastTimestamp;
        }

        byte[] value = new byte[16];
        for (int index = 5; index >= 0; index--) {
            value[index] = (byte) timestamp;
            timestamp >>>= 8;
        }
        System.arraycopy(lastRandom, 0, value, 6, RANDOM_BYTES);
        return encode(value);
    }

    private void incrementRandom() {
        for (int index = lastRandom.length - 1; index >= 0; index--) {
            lastRandom[index]++;
            if (lastRandom[index] != 0) {
                return;
            }
        }
        throw new IllegalStateException("COMMON_ULID_RANDOM_OVERFLOW");
    }

    private String encode(byte[] value) {
        BigInteger remaining = new BigInteger(1, value);
        char[] encoded = new char[26];
        BigInteger radix = BigInteger.valueOf(32);
        for (int index = encoded.length - 1; index >= 0; index--) {
            BigInteger[] quotientAndRemainder = remaining.divideAndRemainder(radix);
            encoded[index] = ENCODING[quotientAndRemainder[1].intValue()];
            remaining = quotientAndRemainder[0];
        }
        return new String(encoded);
    }
}
