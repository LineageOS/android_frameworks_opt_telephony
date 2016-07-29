package com.mediatek.internal.telephony.ltedc;

/**
 * LTE DC constants for reference.
 */
public final class LteDcConstants {
    // Attention to not overlap with Google default DctConstants.
    public static final int BASE_IRAT_DATA_CONNECTION = 0x00045000;
    public static final int EVENT_IRAT_DATA_RAT_CHANGED = BASE_IRAT_DATA_CONNECTION + 0;
    public static final int EVENT_LTE_RECORDS_LOADED = BASE_IRAT_DATA_CONNECTION + 1;
    public static final int EVENT_RETRY_SETUP_DATA_FOR_IRAT = BASE_IRAT_DATA_CONNECTION + 2;

    // PS service is on CDMA or LTE.
    public static final int PS_SERVICE_UNKNOWN = -1;
    public static final int PS_SERVICE_ON_CDMA = 0;
    public static final int PS_SERVICE_ON_LTE = 1;
}
