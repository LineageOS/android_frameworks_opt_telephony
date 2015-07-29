/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codeaurora;

/**
 * The class contains definitions for Qti specific constants related to any value added features
 * for video telephony.
 */
/**
 * @hide
 */
public class QtiVideoCallConstants {

    /**
     * Call substate bitmask values
     */
    /* Default case */
    public static final int CALL_SUBSTATE_NONE = 0;

    /* Indicates that the call is connected but audio attribute is suspended */
    public static final int CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED = 0x1;

    /* Indicates that the call is connected but video attribute is suspended */
    public static final int CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED = 0x2;

    /* Indicates that the call is established but media retry is needed */
    public static final int CALL_SUBSTATE_AVP_RETRY = 0x4;

    /* Indicates that the call is multitasking */
    public static final int CALL_SUBSTATE_MEDIA_PAUSED = 0x8;

    /* Mask containing all the call substate bits set */
    public static final int CALL_SUBSTATE_ALL = CALL_SUBSTATE_AUDIO_CONNECTED_SUSPENDED |
        CALL_SUBSTATE_VIDEO_CONNECTED_SUSPENDED | CALL_SUBSTATE_AVP_RETRY |
        CALL_SUBSTATE_MEDIA_PAUSED;

    /* Call substate extra key name */
    public static final String CALL_SUBSTATE_EXTRA_KEY = "CallSubstate";

    /**
     * Private constructor. This class should not be instantiated.
     */
    private QtiVideoCallConstants() {
    }
}

