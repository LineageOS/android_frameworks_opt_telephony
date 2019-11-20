package com.android.internal.telephony;

import android.content.Context;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.telephony.Rlog;

/**
 * {@hide} Checks for blocked phone numbers against {@link BlockedNumberContract}
 */
public class BlockChecker {
    private static final String TAG = "BlockChecker";
    private static final boolean VDBG = false; // STOPSHIP if true.

    /**
     * Returns {@code true} if {@code phoneNumber} is blocked according to {@code extras}.
     * <p>
     * This method catches all underlying exceptions to ensure that this method never throws any
     * exception.
     * <p>
     * @deprecated use {@link #isBlocked(Context, String, Bundle)} instead.
     *
     * @param context the context of the caller.
     * @param phoneNumber the number to check.
     * @return {@code true} if the number is blocked. {@code false} otherwise.
     */
    @Deprecated
    public static boolean isBlocked(Context context, String phoneNumber) {
        return isBlocked(context, phoneNumber, null /* extras */);
    }

    /**
     * Returns {@code true} if {@code phoneNumber} is blocked according to {@code extras}.
     * <p>
     * This method catches all underlying exceptions to ensure that this method never throws any
     * exception.
     *
     * @param context the context of the caller.
     * @param phoneNumber the number to check.
     * @param extras the extra attribute of the number.
     * @return {@code true} if the number is blocked. {@code false} otherwise.
     */
    public static boolean isBlocked(Context context, String phoneNumber, Bundle extras) {
        return getBlockStatus(context, phoneNumber, extras)
                != BlockedNumberContract.STATUS_NOT_BLOCKED;
    }

    /**
     * Returns the call blocking status for the {@code phoneNumber}.
     * <p>
     * This method catches all underlying exceptions to ensure that this method never throws any
     * exception.
     *
     * @param context the context of the caller.
     * @param phoneNumber the number to check.
     * @param extras the extra attribute of the number.
     * @return result code indicating if the number should be blocked, and if so why.
     *         Valid values are: {@link BlockedNumberContract#STATUS_NOT_BLOCKED},
     *         {@link BlockedNumberContract#STATUS_BLOCKED_IN_LIST},
     *         {@link BlockedNumberContract#STATUS_BLOCKED_NOT_IN_CONTACTS},
     *         {@link BlockedNumberContract#STATUS_BLOCKED_PAYPHONE},
     *         {@link BlockedNumberContract#STATUS_BLOCKED_RESTRICTED},
     *         {@link BlockedNumberContract#STATUS_BLOCKED_UNKNOWN_NUMBER}.
     */
    public static int getBlockStatus(Context context, String phoneNumber, Bundle extras) {
        int blockStatus = BlockedNumberContract.STATUS_NOT_BLOCKED;
        long startTimeNano = System.nanoTime();

        try {
            blockStatus = shouldSystemBlockNumber(context, phoneNumber, extras);
            if (blockStatus != BlockedNumberContract.STATUS_NOT_BLOCKED) {
                Rlog.d(TAG, phoneNumber + " is blocked.");
            }
        } catch (Exception e) {
            Rlog.e(TAG, "Exception checking for blocked number: " + e);
        }
        int durationMillis = (int) ((System.nanoTime() - startTimeNano) / 1000000);
        if (durationMillis > 500 || VDBG) {
            Rlog.d(TAG, "Blocked number lookup took: " + durationMillis + " ms.");
        }
        return blockStatus;
    }

    /**
     * Returns {@code true} if {@code phoneNumber} is blocked taking
     * {@link #notifyEmergencyContact(Context)} into consideration. If emergency services
     * have not been contacted recently and enhanced call blocking not been enabled, this
     * method is equivalent to {@link #isBlocked(Context, String)}.
     *
     * @param context the context of the caller.
     * @param phoneNumber the number to check.
     * @param extras the extra attribute of the number.
     * @return result code indicating if the number should be blocked, and if so why.
     *         Valid values are: {@link #STATUS_NOT_BLOCKED}, {@link #STATUS_BLOCKED_IN_LIST},
     *         {@link #STATUS_BLOCKED_NOT_IN_CONTACTS}, {@link #STATUS_BLOCKED_PAYPHONE},
     *         {@link #STATUS_BLOCKED_RESTRICTED}, {@link #STATUS_BLOCKED_UNKNOWN_NUMBER}.
     */
    private static int shouldSystemBlockNumber(Context context, String phoneNumber,
                                              Bundle extras) {
        try {
            String caller = context.getOpPackageName();
            final Bundle res = context.getContentResolver().call(
                    BlockedNumberContract.AUTHORITY_URI,
                    BlockedNumberContract.METHOD_SHOULD_SYSTEM_BLOCK_NUMBER,
                    phoneNumber, extras);
            int blockResult = res != null ? res.getInt(BlockedNumberContract.RES_BLOCK_STATUS,
                    BlockedNumberContract.STATUS_NOT_BLOCKED) :
                    BlockedNumberContract.STATUS_NOT_BLOCKED;
            Rlog.d(TAG, "shouldSystemBlockNumber: number=" + Rlog.pii(TAG, phoneNumber)
                    + "caller=" + caller + "result=" + blockStatusToString(blockResult));
            return blockResult;
        } catch (NullPointerException | IllegalArgumentException ex) {
            // The content resolver can throw an NPE or IAE; we don't want to crash Telecom if
            // either of these happen.
            Rlog.w(null, "shouldSystemBlockNumber: provider not ready.");
            return BlockedNumberContract.STATUS_NOT_BLOCKED;
        }
    }

    /**
     * Converts a block status constant to a string equivalent for logging.
     * @hide
     */
    private static String blockStatusToString(int blockStatus) {
        switch (blockStatus) {
            case BlockedNumberContract.STATUS_NOT_BLOCKED:
                return "not blocked";
            case BlockedNumberContract.STATUS_BLOCKED_IN_LIST:
                return "blocked - in list";
            case BlockedNumberContract.STATUS_BLOCKED_RESTRICTED:
                return "blocked - restricted";
            case BlockedNumberContract.STATUS_BLOCKED_UNKNOWN_NUMBER:
                return "blocked - unknown";
            case BlockedNumberContract.STATUS_BLOCKED_PAYPHONE:
                return "blocked - payphone";
            case BlockedNumberContract.STATUS_BLOCKED_NOT_IN_CONTACTS:
                return "blocked - not in contacts";
        }
        return "unknown";
    }

}
