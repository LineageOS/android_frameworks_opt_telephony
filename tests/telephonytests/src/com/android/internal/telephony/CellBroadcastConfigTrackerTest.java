/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static com.android.internal.telephony.CellBroadcastConfigTracker.mergeRangesAsNeeded;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Message;
import android.telephony.CellBroadcastIdRange;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public final class CellBroadcastConfigTrackerTest extends TelephonyTest {

    private CommandsInterface mSpyCi;
    private CellBroadcastConfigTracker mTracker;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSpyCi = spy(mSimulatedCommands);
        mPhone = new GsmCdmaPhone(mContext, mSpyCi, mNotifier, true, 0,
            PhoneConstants.PHONE_TYPE_GSM, mTelephonyComponentFactory, (c, p) -> mImsManager);
        mTracker = CellBroadcastConfigTracker.make(mPhone, mPhone, true);
        mPhone.mCellBroadcastConfigTracker = mTracker;
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mPhone.removeCallbacksAndMessages(null);
        mPhone = null;
        super.tearDown();
    }

    @Test
    public void testSetCellBroadcastIdRangesSuccess() throws Exception {
        final int[][] channelValues = {
            {0, 999}, {1000, 1003}, {1004, 0x0FFF}, {0x1000, 0x10FF}, {0x1100, 0x112F},
            {0x1130, 0x1900}, {0x1901, 0x9FFF}, {0xA000, 0xFFFE}, {0xFFFF, 0xFFFF}};
        List<CellBroadcastIdRange> ranges = new ArrayList<>();
        for (int i = 0; i < channelValues.length; i++) {
            ranges.add(new CellBroadcastIdRange(channelValues[i][0], channelValues[i][1],
                    SmsCbMessage.MESSAGE_FORMAT_3GPP, i > 0 ? true : false));
        }

        List<SmsBroadcastConfigInfo> gsmConfigs = new ArrayList<>();
        gsmConfigs.add(new SmsBroadcastConfigInfo(0, 999, 0, 255, false));
        gsmConfigs.add(new SmsBroadcastConfigInfo(1000, 0xFFFF, 0, 255, true));

        ArgumentCaptor<SmsBroadcastConfigInfo[]> gsmCaptor = ArgumentCaptor.forClass(
                SmsBroadcastConfigInfo[].class);
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

        mockCommandInterface();

        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS == r));
        processAllMessages();

        verify(mSpyCi, times(1)).setGsmBroadcastConfig(gsmCaptor.capture(), msgCaptor.capture());
        List<SmsBroadcastConfigInfo> gsmArgs = Arrays.asList(
                (SmsBroadcastConfigInfo[]) gsmCaptor.getValue());
        assertEquals(gsmConfigs, gsmArgs);

        Message msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(1)).setGsmBroadcastActivation(eq(true), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, never()).setCdmaBroadcastConfig(any(), any());
        verify(mSpyCi, never()).setCdmaBroadcastActivation(anyBoolean(), any());

        assertEquals(mPhone.getCellBroadcastIdRanges(), mergeRangesAsNeeded(ranges));

        //Verify to set cdma config and activate, but no more for gsm as no change
        for (int i = 0; i < channelValues.length; i++) {
            ranges.add(new CellBroadcastIdRange(channelValues[i][0], channelValues[i][1],
                    SmsCbMessage.MESSAGE_FORMAT_3GPP2, i > 0 ? true : false));
        }
        List<CdmaSmsBroadcastConfigInfo> cdmaConfigs = new ArrayList<>();
        cdmaConfigs.add(new CdmaSmsBroadcastConfigInfo(0, 999, 1, false));
        cdmaConfigs.add(new CdmaSmsBroadcastConfigInfo(1000, 0xFFFF, 1, true));
        ArgumentCaptor<CdmaSmsBroadcastConfigInfo[]> cdmaCaptor = ArgumentCaptor.forClass(
                CdmaSmsBroadcastConfigInfo[].class);

        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS == r));
        processAllMessages();

        verify(mSpyCi, times(1)).setGsmBroadcastConfig(any(), any());
        verify(mSpyCi, times(1)).setCdmaBroadcastConfig(cdmaCaptor.capture(), msgCaptor.capture());
        List<CdmaSmsBroadcastConfigInfo> cdmaArgs = Arrays.asList(
                (CdmaSmsBroadcastConfigInfo[]) cdmaCaptor.getValue());
        assertEquals(cdmaConfigs, cdmaArgs);

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(1)).setGsmBroadcastActivation(anyBoolean(), any());
        verify(mSpyCi, times(1)).setCdmaBroadcastActivation(eq(true), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        assertEquals(mPhone.getCellBroadcastIdRanges(), mergeRangesAsNeeded(ranges));

        // Verify not to set cdma or gsm config as the config is not changed
        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS == r));
        processAllMessages();

        verify(mSpyCi, times(1)).setCdmaBroadcastConfig(any(), any());
        verify(mSpyCi, times(1)).setCdmaBroadcastActivation(anyBoolean(), any());
        verify(mSpyCi, times(1)).setGsmBroadcastConfig(any(), any());
        verify(mSpyCi, times(1)).setGsmBroadcastActivation(anyBoolean(), any());

        assertEquals(mPhone.getCellBroadcastIdRanges(), mergeRangesAsNeeded(ranges));

        // Verify to reset ranges with empty ranges list
        mPhone.setCellBroadcastIdRanges(new ArrayList<>(), r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS == r));

        processAllMessages();

        verify(mSpyCi, times(2)).setGsmBroadcastConfig(gsmCaptor.capture(), msgCaptor.capture());
        assertEquals(0, ((SmsBroadcastConfigInfo[]) gsmCaptor.getValue()).length);

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        // Verify to deavtivate gsm broadcast on empty ranges
        verify(mSpyCi, times(1)).setGsmBroadcastActivation(eq(false), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(2)).setCdmaBroadcastConfig(cdmaCaptor.capture(), msgCaptor.capture());
        assertEquals(0, ((CdmaSmsBroadcastConfigInfo[]) cdmaCaptor.getValue()).length);

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        // Verify to deavtivate cdma broadcast on empty ranges
        verify(mSpyCi, times(1)).setCdmaBroadcastActivation(eq(false), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        assertTrue(mPhone.getCellBroadcastIdRanges().isEmpty());

        //Verify to set gsm and cdma config then activate again
        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS == r));

        processAllMessages();

        verify(mSpyCi, times(3)).setGsmBroadcastConfig(gsmCaptor.capture(), msgCaptor.capture());
        gsmArgs = Arrays.asList((SmsBroadcastConfigInfo[]) gsmCaptor.getValue());
        assertEquals(gsmConfigs, gsmArgs);

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(2)).setGsmBroadcastActivation(eq(true), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(3)).setCdmaBroadcastConfig(cdmaCaptor.capture(), msgCaptor.capture());
        cdmaArgs = Arrays.asList((CdmaSmsBroadcastConfigInfo[]) cdmaCaptor.getValue());
        assertEquals(cdmaConfigs, cdmaArgs);

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(2)).setCdmaBroadcastActivation(eq(true), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        assertEquals(mPhone.getCellBroadcastIdRanges(), mergeRangesAsNeeded(ranges));
    }

    @Test
    public void testSetCellBroadcastIdRangesFailure() throws Exception {
        List<CellBroadcastIdRange> ranges = new ArrayList<>();

        // Verify to throw exception for invalid ranges
        ranges.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true));
        ranges.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, false));

        assertThrows(IllegalArgumentException.class,
                () -> mPhone.setCellBroadcastIdRanges(ranges, r -> {}));

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        ranges.clear();
        ranges.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true));
        ranges.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP2, true));

        mockCommandInterface();

        // Verify the result on setGsmBroadcastConfig failure
        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_FAIL_CONFIG == r));
        processAllMessages();

        verify(mSpyCi, times(1)).setGsmBroadcastConfig(any(), msgCaptor.capture());

        Message msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg).exception = new RuntimeException();
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(0)).setGsmBroadcastActivation(anyBoolean(), any());
        verify(mSpyCi, times(0)).setCdmaBroadcastConfig(any(), any());
        verify(mSpyCi, times(0)).setCdmaBroadcastActivation(anyBoolean(), any());
        assertTrue(mPhone.getCellBroadcastIdRanges().isEmpty());

        // Verify the result on setGsmBroadcastActivation failure
        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_FAIL_ACTIVATION == r));
        processAllMessages();

        verify(mSpyCi, times(2)).setGsmBroadcastConfig(any(), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(1)).setGsmBroadcastActivation(anyBoolean(), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg).exception = new RuntimeException();
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(0)).setCdmaBroadcastConfig(any(), any());
        verify(mSpyCi, times(0)).setCdmaBroadcastActivation(anyBoolean(), any());
        assertTrue(mPhone.getCellBroadcastIdRanges().isEmpty());

        // Verify the result on setCdmaBroadcastConfig failure
        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_FAIL_CONFIG == r));
        processAllMessages();

        verify(mSpyCi, times(3)).setGsmBroadcastConfig(any(), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(2)).setGsmBroadcastActivation(anyBoolean(), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(1)).setCdmaBroadcastConfig(any(), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg).exception = new RuntimeException();
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(0)).setCdmaBroadcastActivation(anyBoolean(), any());

        List<CellBroadcastIdRange> ranges3gpp = new ArrayList<>();
        ranges3gpp.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true));

        assertEquals(mPhone.getCellBroadcastIdRanges(), ranges3gpp);

        // Verify the result on setCdmaBroadcastActivation failure
        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_FAIL_ACTIVATION == r));
        processAllMessages();

        // Verify no more calls as there is no change of ranges for 3gpp
        verify(mSpyCi, times(3)).setGsmBroadcastConfig(any(), any());
        verify(mSpyCi, times(2)).setGsmBroadcastActivation(anyBoolean(), any());
        verify(mSpyCi, times(2)).setCdmaBroadcastConfig(any(), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        verify(mSpyCi, times(1)).setCdmaBroadcastActivation(anyBoolean(), msgCaptor.capture());

        msg = msgCaptor.getValue();
        assertNotNull(msg);
        AsyncResult.forMessage(msg).exception = new RuntimeException();
        msg.sendToTarget();
        processAllMessages();

        assertEquals(mPhone.getCellBroadcastIdRanges(), ranges3gpp);
    }

    @Test
    public void testClearCellBroadcastConfigOnRadioOff() {
        List<CellBroadcastIdRange> ranges = new ArrayList<>();
        ranges.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true));

        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS == r));
        processAllMessages();

        assertEquals(mPhone.getCellBroadcastIdRanges(), ranges);

        mPhone.sendEmptyMessage(Phone.EVENT_RADIO_OFF_OR_NOT_AVAILABLE);
        processAllMessages();

        // Verify the config is reset
        assertTrue(mPhone.getCellBroadcastIdRanges().isEmpty());
    }

    @Test
    public void testClearCellBroadcastConfigOnSubscriptionChanged() {
        List<CellBroadcastIdRange> ranges = new ArrayList<>();
        ranges.add(new CellBroadcastIdRange(0, 999, SmsCbMessage.MESSAGE_FORMAT_3GPP, true));

        mPhone.setCellBroadcastIdRanges(ranges, r -> assertTrue(
                TelephonyManager.CELL_BROADCAST_RESULT_SUCCESS == r));
        processAllMessages();

        assertEquals(mPhone.getCellBroadcastIdRanges(), ranges);

        mTracker.mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        // Verify the config is not reset when the sub id is not changed
        assertEquals(mPhone.getCellBroadcastIdRanges(), ranges);

        mTracker.mSubId = mTracker.mSubId % SubscriptionManager.DEFAULT_SUBSCRIPTION_ID + 1;

        mTracker.mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        // Verify the config is reset when the sub id is changed
        assertTrue(mPhone.getCellBroadcastIdRanges().isEmpty());
    }

    @Test
    public void testMergeCellBroadcastIdRangesAsNeeded() {
        final int[][] channelValues = {
                {0, 999}, {1000, 1003}, {1004, 0x0FFF}, {0x1000, 0x10FF}, {0x1100, 0x112F},
                {0x1130, 0x1900}, {0x1901, 0x9FFF}, {0xA000, 0xFFFE}, {0xFFFF, 0xFFFF}};
        final int[] typeValues = {
                SmsCbMessage.MESSAGE_FORMAT_3GPP, SmsCbMessage.MESSAGE_FORMAT_3GPP2};
        final boolean[] enabledValues = {true, false};

        List<CellBroadcastIdRange> ranges = new ArrayList<>();
        for (int i = 0; i < channelValues.length; i++) {
            ranges.add(new CellBroadcastIdRange(channelValues[i][0], channelValues[i][1],
                    typeValues[0], enabledValues[0]));
        }

        ranges = mergeRangesAsNeeded(ranges);

        assertEquals(1, ranges.size());
        assertEquals(ranges.get(0).getStartId(), channelValues[0][0]);
        assertEquals(ranges.get(0).getEndId(), channelValues[channelValues.length - 1][0]);

        // Verify not to merge the ranges with different types.
        ranges.clear();
        for (int i = 0; i < channelValues.length; i++) {
            ranges.add(new CellBroadcastIdRange(channelValues[i][0], channelValues[i][1],
                    typeValues[0], enabledValues[0]));
            ranges.add(new CellBroadcastIdRange(channelValues[i][0], channelValues[i][1],
                    typeValues[1], enabledValues[0]));
        }

        ranges = mergeRangesAsNeeded(ranges);

        assertEquals(2, ranges.size());
        assertEquals(ranges.get(0).getStartId(), channelValues[0][0]);
        assertEquals(ranges.get(0).getEndId(), channelValues[channelValues.length - 1][0]);
        assertEquals(ranges.get(1).getStartId(), channelValues[0][0]);
        assertEquals(ranges.get(1).getEndId(), channelValues[channelValues.length - 1][0]);
        assertTrue(ranges.get(0).getType() != ranges.get(1).getType());

        // Verify to throw IllegalArgumentException if the same range is enabled and disabled
        // in the range list.
        final List<CellBroadcastIdRange> ranges2 = new ArrayList<>();
        for (int i = 0; i < channelValues.length; i++) {
            ranges2.add(new CellBroadcastIdRange(channelValues[i][0], channelValues[i][1],
                    typeValues[0], enabledValues[0]));
            ranges2.add(new CellBroadcastIdRange(channelValues[i][0], channelValues[i][1],
                    typeValues[0], enabledValues[1]));
        }

        assertThrows(IllegalArgumentException.class, () ->
                mergeRangesAsNeeded(ranges2));
    }

    @Test
    public void testMakeCellBroadcastConfigTracker() {
        Phone phone = spy(mPhone);
        CellBroadcastConfigTracker tracker = CellBroadcastConfigTracker.make(phone, phone, false);
        processAllMessages();

        verify(phone, never()).registerForRadioOffOrNotAvailable(any(), anyInt(), any());
        verify(mSubscriptionManager, never()).addOnSubscriptionsChangedListener(
                any(), eq(tracker.mSubChangedListener));

        tracker.start();
        processAllMessages();

        verify(phone, times(1)).registerForRadioOffOrNotAvailable(any(), anyInt(), any());
        verify(mSubscriptionManager, times(1)).addOnSubscriptionsChangedListener(
                any(), eq(tracker.mSubChangedListener));

        tracker = CellBroadcastConfigTracker.make(phone, phone, true);
        processAllMessages();

        verify(phone, times(2)).registerForRadioOffOrNotAvailable(any(), anyInt(), any());
        verify(mSubscriptionManager, times(1)).addOnSubscriptionsChangedListener(
                any(), eq(tracker.mSubChangedListener));
    }

    private void mockCommandInterface() {
        doNothing().when(mSpyCi).setGsmBroadcastConfig(any(), any());
        doNothing().when(mSpyCi).setGsmBroadcastActivation(anyBoolean(), any());
        doNothing().when(mSpyCi).setCdmaBroadcastConfig(any(), any());
        doNothing().when(mSpyCi).setCdmaBroadcastActivation(anyBoolean(), any());
    }
}
