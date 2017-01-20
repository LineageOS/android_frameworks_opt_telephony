/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;

import com.android.ims.ImsConfig;
import com.android.ims.ImsReasonInfo;
import com.android.ims.internal.ImsCallSession;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.GsmCdmaConnection;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.TelephonyProto;
import com.android.internal.telephony.TelephonyProto.ImsConnectionState;
import com.android.internal.telephony.TelephonyProto.SmsSession;
import com.android.internal.telephony.TelephonyProto.RadioAccessTechnology;
import com.android.internal.telephony.TelephonyProto.TelephonyCallSession;
import com.android.internal.telephony.TelephonyProto.TelephonyCallSession.Event.CallState;
import com.android.internal.telephony.TelephonyProto.TelephonyCallSession.Event.ImsCommand;
import com.android.internal.telephony.TelephonyProto.TelephonyCallSession.Event.RilCall;
import com.android.internal.telephony.TelephonyProto.TelephonyEvent;
import com.android.internal.telephony.TelephonyProto.TelephonyLog;
import com.android.internal.telephony.TelephonyProto.TelephonyServiceState;
import com.android.internal.telephony.TelephonyProto.TelephonyServiceState.RoamingType;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.dataconnection.DataCallResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.lang.reflect.Method;

import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
import static android.telephony.ServiceState.ROAMING_TYPE_DOMESTIC;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DEACTIVATE_DATA_CALL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_SMS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SETUP_DATA_CALL;
import static com.android.internal.telephony.TelephonyProto.PdpType.PDP_TYPE_IPV4V6;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class TelephonyMetricsTest extends TelephonyTest {

    @Mock
    private ImsCallSession mImsCallSession;

    @Mock
    private ImsReasonInfo mImsReasonInfo;

    @Mock
    private ServiceState mServiceState;

    @Mock
    private GsmCdmaConnection mConnection;

    private TelephonyMetrics mMetrics;

    private UUSInfo mUusInfo;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMetrics = new TelephonyMetrics();
        mUusInfo = new UUSInfo(1, 2, new byte[]{1, 2});
        doReturn("123").when(mImsCallSession).getCallId();
        doReturn("extramessage").when(mImsReasonInfo).getExtraMessage();
        doReturn(123).when(mImsReasonInfo).getCode();
        doReturn(456).when(mImsReasonInfo).getExtraCode();

        doReturn(ROAMING_TYPE_DOMESTIC).when(mServiceState).getVoiceRoamingType();
        doReturn(ROAMING_TYPE_DOMESTIC).when(mServiceState).getDataRoamingType();
        doReturn("voiceshort").when(mServiceState).getVoiceOperatorAlphaShort();
        doReturn("voicelong").when(mServiceState).getVoiceOperatorAlphaLong();
        doReturn("datashort").when(mServiceState).getDataOperatorAlphaShort();
        doReturn("datalong").when(mServiceState).getDataOperatorAlphaLong();
        doReturn("123456").when(mServiceState).getVoiceOperatorNumeric();
        doReturn("123456").when(mServiceState).getDataOperatorNumeric();
        doReturn(RIL_RADIO_TECHNOLOGY_LTE).when(mServiceState).getRilVoiceRadioTechnology();
        doReturn(RIL_RADIO_TECHNOLOGY_LTE).when(mServiceState).getRilDataRadioTechnology();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private TelephonyLog buildProto() throws Exception {
        Method method = TelephonyMetrics.class.getDeclaredMethod("buildProto");
        method.setAccessible(true);
        return (TelephonyLog) method.invoke(mMetrics);
    }

    private void reset() throws Exception {
        Method method = TelephonyMetrics.class.getDeclaredMethod("reset");
        method.setAccessible(true);
        method.invoke(mMetrics);
    }

    private String convertProtoToBase64String(TelephonyLog log) throws Exception {
        Class[] cArgs = new Class[1];
        cArgs[0] = TelephonyLog.class;
        Method method = TelephonyMetrics.class.getDeclaredMethod("convertProtoToBase64String",
                cArgs);
        method.setAccessible(true);
        return (String) method.invoke(null, log);
    }

    @Test
    @SmallTest
    public void testEventDropped() throws Exception {
        for (int i = 0; i < 1001; i++) {
            mMetrics.writeDataStallEvent(mPhone.getPhoneId(), i);
        }
        TelephonyLog log = buildProto();
        assertEquals(1000, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertTrue(log.getEventsDropped());
        assertEquals(1, log.events[0].getDataStallAction());
    }

    // Test write data stall event
    @Test
    @SmallTest
    public void testWriteDataStallEvent() throws Exception {
        mMetrics.writeDataStallEvent(mPhone.getPhoneId(), 3);
        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.events[0].hasPhoneId());
        assertEquals(mPhone.getPhoneId(), log.events[0].getPhoneId());
        assertEquals(3, log.events[0].getDataStallAction());
    }

    // Test write on IMS call start
    @Test
    @SmallTest
    public void testWriteOnImsCallStart() throws Exception {
        mMetrics.writeOnImsCallStart(mPhone.getPhoneId(), mImsCallSession);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(0, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.callSessions[0].hasPhoneId());
        assertEquals(mPhone.getPhoneId(), log.callSessions[0].getPhoneId());
        assertTrue(log.callSessions[0].hasEventsDropped());
        assertFalse(log.callSessions[0].getEventsDropped());
        assertTrue(log.callSessions[0].hasStartTimeMinutes());
        assertEquals(1, log.callSessions[0].events.length);
        assertTrue(log.callSessions[0].events[0].hasCallIndex());
        assertEquals(123, log.callSessions[0].events[0].getCallIndex());
        assertTrue(log.callSessions[0].events[0].hasImsCommand());
        assertEquals(ImsCommand.IMS_CMD_START, log.callSessions[0].events[0].getImsCommand());
    }

    // Test write ims call state
    @Test
    @SmallTest
    public void testWriteImsCallState() throws Exception {
        mMetrics.writeOnImsCallStart(mPhone.getPhoneId(), mImsCallSession);
        mMetrics.writeImsCallState(mPhone.getPhoneId(), mImsCallSession, Call.State.ACTIVE);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(0, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertEquals(2, log.callSessions[0].events.length);
        assertTrue(log.callSessions[0].hasEventsDropped());
        assertFalse(log.callSessions[0].getEventsDropped());
        assertTrue(log.callSessions[0].events[1].hasCallIndex());
        assertEquals(123, log.callSessions[0].events[1].getCallIndex());
        assertTrue(log.callSessions[0].events[1].hasCallState());
        assertEquals(CallState.CALL_ACTIVE, log.callSessions[0].events[1].getCallState());
    }

    // Test write ims set feature value
    @Test
    @SmallTest
    public void testWriteImsSetFeatureValue() throws Exception {
        mMetrics.writeOnImsCallStart(mPhone.getPhoneId(), mImsCallSession);
        mMetrics.writeImsSetFeatureValue(mPhone.getPhoneId(),
                ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE, 0, 1, 0);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertEquals(2, log.callSessions[0].events.length);
        assertTrue(log.callSessions[0].hasEventsDropped());
        assertFalse(log.callSessions[0].getEventsDropped());
        assertTrue(log.callSessions[0].events[1].settings.hasIsEnhanced4GLteModeEnabled());
        assertTrue(log.callSessions[0].events[1].settings.getIsEnhanced4GLteModeEnabled());
    }

    // Test write on ims call handover event
    @Test
    @SmallTest
    public void testWriteOnImsCallHandoverEvent() throws Exception {
        mMetrics.writeOnImsCallStart(mPhone.getPhoneId(), mImsCallSession);
        mMetrics.writeOnImsCallHandoverEvent(mPhone.getPhoneId(),
                TelephonyCallSession.Event.Type.IMS_CALL_HANDOVER, mImsCallSession, 5, 6,
                mImsReasonInfo);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(0, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertEquals(2, log.callSessions[0].events.length);
        assertTrue(log.callSessions[0].hasEventsDropped());
        assertFalse(log.callSessions[0].getEventsDropped());
        assertTrue(log.callSessions[0].events[1].hasType());
        assertEquals(TelephonyCallSession.Event.Type.IMS_CALL_HANDOVER,
                log.callSessions[0].events[1].getType());
        assertTrue(log.callSessions[0].events[1].hasCallIndex());
        assertEquals(123, log.callSessions[0].events[1].getCallIndex());
        assertTrue(log.callSessions[0].events[1].hasSrcAccessTech());
        assertEquals(5, log.callSessions[0].events[1].getSrcAccessTech());
        assertTrue(log.callSessions[0].events[1].hasTargetAccessTech());
        assertEquals(6, log.callSessions[0].events[1].getTargetAccessTech());

        assertTrue(log.callSessions[0].events[1].reasonInfo.hasExtraMessage());
        assertEquals("extramessage", log.callSessions[0].events[1].reasonInfo.getExtraMessage());
        assertTrue(log.callSessions[0].events[1].reasonInfo.hasExtraCode());
        assertEquals(456, log.callSessions[0].events[1].reasonInfo.getExtraCode());
        assertTrue(log.callSessions[0].events[1].reasonInfo.hasReasonCode());
        assertEquals(123, log.callSessions[0].events[1].reasonInfo.getReasonCode());
    }

    // Test write on ims command
    @Test
    @SmallTest
    public void testWriteOnImsCommand() throws Exception {
        mMetrics.writeOnImsCallStart(mPhone.getPhoneId(), mImsCallSession);
        mMetrics.writeOnImsCommand(mPhone.getPhoneId(), mImsCallSession, 123);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(0, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertEquals(2, log.callSessions[0].events.length);
        assertTrue(log.callSessions[0].hasEventsDropped());
        assertFalse(log.callSessions[0].getEventsDropped());
        assertTrue(log.callSessions[0].events[1].hasType());
        assertEquals(TelephonyCallSession.Event.Type.IMS_COMMAND,
                log.callSessions[0].events[1].getType());
        assertTrue(log.callSessions[0].events[1].hasImsCommand());
        assertEquals(123, log.callSessions[0].events[1].getImsCommand());
        assertTrue(log.callSessions[0].events[1].hasCallIndex());
        assertEquals(123, log.callSessions[0].events[1].getCallIndex());
    }

    // Test write on ims connection state
    @Test
    @SmallTest
    public void testWriteOnImsConnectionState() throws Exception {
        mMetrics.writeOnImsCallStart(mPhone.getPhoneId(), mImsCallSession);
        mMetrics.writeOnImsConnectionState(mPhone.getPhoneId(),
                ImsConnectionState.State.CONNECTED, mImsReasonInfo);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertEquals(2, log.callSessions[0].events.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());
        assertTrue(log.events[0].hasType());
        assertEquals(TelephonyEvent.Type.IMS_CONNECTION_STATE_CHANGED, log.events[0].getType());
        assertTrue(log.events[0].imsConnectionState.hasState());
        assertEquals(ImsConnectionState.State.CONNECTED,
                log.events[0].imsConnectionState.getState());
        assertTrue(log.events[0].imsConnectionState.reasonInfo.hasReasonCode());
        assertEquals(123, log.events[0].imsConnectionState.reasonInfo.getReasonCode());
        assertTrue(log.events[0].imsConnectionState.reasonInfo.hasExtraCode());
        assertEquals(456, log.events[0].imsConnectionState.reasonInfo.getExtraCode());
        assertTrue(log.events[0].imsConnectionState.reasonInfo.hasExtraMessage());
        assertEquals("extramessage", log.events[0].imsConnectionState.reasonInfo.getExtraMessage());
        assertTrue(log.callSessions[0].hasEventsDropped());
        assertFalse(log.callSessions[0].getEventsDropped());
        assertTrue(log.callSessions[0].events[1].hasType());
        assertEquals(TelephonyCallSession.Event.Type.IMS_CONNECTION_STATE_CHANGED,
                log.callSessions[0].events[1].getType());
        assertTrue(log.callSessions[0].events[1].imsConnectionState.hasState());
        assertEquals(ImsConnectionState.State.CONNECTED,
                log.callSessions[0].events[1].imsConnectionState.getState());
    }

    // Test write on setup data call response
    @Test
    @SmallTest
    public void testWriteOnSetupDataCallResponse() throws Exception {
        DataCallResponse response = new DataCallResponse();
        response.status = 5;
        response.suggestedRetryTime = 6;
        response.cid = 7;
        response.active = 8;
        response.type = "IPV4V6";
        response.ifname = "ifname";

        mMetrics.writeOnRilSolicitedResponse(mPhone.getPhoneId(), 1, 2,
                RIL_REQUEST_SETUP_DATA_CALL, response);
        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        TelephonyEvent.RilSetupDataCallResponse respProto = log.events[0].setupDataCallResponse;

        assertTrue(respProto.hasStatus());
        assertEquals(5, respProto.getStatus());
        assertTrue(respProto.hasSuggestedRetryTimeMillis());
        assertEquals(6, respProto.getSuggestedRetryTimeMillis());
        assertTrue(respProto.call.hasCid());
        assertEquals(7, respProto.call.getCid());
        assertTrue(respProto.call.hasType());
        assertEquals(PDP_TYPE_IPV4V6, respProto.call.getType());
        assertTrue(respProto.call.hasIframe());
        assertEquals("ifname", respProto.call.getIframe());
    }

    // Test write on deactivate data call response
    @Test
    @SmallTest
    public void testWriteOnDeactivateDataCallResponse() throws Exception {
        mMetrics.writeOnRilSolicitedResponse(mPhone.getPhoneId(), 2, 3,
                RIL_REQUEST_DEACTIVATE_DATA_CALL, null);
        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        assertTrue(log.events[0].hasType());
        assertEquals(TelephonyEvent.Type.DATA_CALL_DEACTIVATE_RESPONSE, log.events[0].getType());
        assertTrue(log.events[0].hasError());
        assertEquals(4, log.events[0].getError());
    }

    // Test write RIL send SMS
    @Test
    @SmallTest
    public void testWriteRilSendSms() throws Exception {
        mMetrics.writeRilSendSms(mPhone.getPhoneId(), 1, 2, 1);
        mMetrics.writeRilSendSms(mPhone.getPhoneId(), 4, 5, 2);

        SmsResponse response = new SmsResponse(0, null, 123);

        mMetrics.writeOnRilSolicitedResponse(mPhone.getPhoneId(), 1, 0, RIL_REQUEST_SEND_SMS,
                response);
        response = new SmsResponse(0, null, 456);
        mMetrics.writeOnRilSolicitedResponse(mPhone.getPhoneId(), 4, 0, RIL_REQUEST_SEND_SMS,
                response);
        TelephonyLog log = buildProto();

        assertEquals(0, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(1, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        SmsSession.Event[] events = log.smsSessions[0].events;
        assertEquals(4, events.length);
        assertTrue(events[0].hasType());
        assertEquals(SmsSession.Event.Type.SMS_SEND, events[0].getType());
        assertTrue(events[0].hasRilRequestId());
        assertEquals(1, events[0].getRilRequestId());
        assertTrue(events[0].hasTech());
        assertEquals(2, events[0].getTech());
        assertTrue(events[0].hasFormat());
        assertEquals(1, events[0].getFormat());

        assertTrue(events[1].hasType());
        assertEquals(SmsSession.Event.Type.SMS_SEND, events[1].getType());
        assertTrue(events[1].hasRilRequestId());
        assertEquals(4, events[1].getRilRequestId());
        assertTrue(events[1].hasTech());
        assertEquals(5, events[1].getTech());
        assertTrue(events[1].hasFormat());
        assertEquals(2, events[1].getFormat());

        assertTrue(events[2].hasType());
        assertEquals(SmsSession.Event.Type.SMS_SEND_RESULT, events[2].getType());
        assertTrue(events[2].hasRilRequestId());
        assertEquals(1, events[2].getRilRequestId());
        assertTrue(events[2].hasError());
        assertEquals(0, events[2].getError());
        assertTrue(events[2].hasErrorCode());
        assertEquals(123, events[2].getErrorCode());

        assertTrue(events[3].hasType());
        assertEquals(SmsSession.Event.Type.SMS_SEND_RESULT, events[3].getType());
        assertTrue(events[3].hasRilRequestId());
        assertEquals(4, events[3].getRilRequestId());
        assertTrue(events[3].hasError());
        assertEquals(0, events[3].getError());
        assertTrue(events[3].hasErrorCode());
        assertEquals(456, events[3].getErrorCode());
    }

    // Test write phone state
    @Test
    @SmallTest
    public void testWritePhoneState() throws Exception {
        mMetrics.writeOnImsCallStart(mPhone.getPhoneId(), mImsCallSession);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.OFFHOOK);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(0, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        assertTrue(log.callSessions[0].hasPhoneId());
        assertEquals(mPhone.getPhoneId(), log.callSessions[0].getPhoneId());
        assertEquals(2, log.callSessions[0].events.length);
        assertTrue(log.callSessions[0].events[1].hasType());
        assertEquals(TelephonyCallSession.Event.Type.PHONE_STATE_CHANGED,
                log.callSessions[0].events[1].getType());
        assertTrue(log.callSessions[0].events[1].hasPhoneState());
        assertEquals(TelephonyCallSession.Event.PhoneState.STATE_OFFHOOK,
                log.callSessions[0].events[1].getPhoneState());
    }

    // Test write RIL dial and hangup
    @Test
    @SmallTest
    public void testWriteRilDialHangup() throws Exception {
        doReturn(Call.State.DIALING).when(mConnection).getState();
        mMetrics.writeRilDial(mPhone.getPhoneId(), mConnection, 2, mUusInfo);
        doReturn(Call.State.DISCONNECTED).when(mConnection).getState();
        mMetrics.writeRilHangup(mPhone.getPhoneId(), mConnection, 3);
        mMetrics.writePhoneState(mPhone.getPhoneId(), PhoneConstants.State.IDLE);
        TelephonyLog log = buildProto();

        assertEquals(0, log.events.length);
        assertEquals(1, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        TelephonyCallSession.Event[] events = log.callSessions[0].events;

        assertEquals(2, events.length);
        assertTrue(events[0].hasType());
        assertEquals(TelephonyCallSession.Event.Type.RIL_REQUEST, events[0].getType());
        assertTrue(events[0].hasRilRequest());
        assertEquals(TelephonyCallSession.Event.RilRequest.RIL_REQUEST_DIAL,
                events[0].getRilRequest());
        RilCall[] calls = events[0].calls;
        assertEquals(CallState.CALL_DIALING, calls[0].getState());

        assertTrue(events[1].hasType());
        assertEquals(TelephonyCallSession.Event.Type.RIL_REQUEST, events[1].getType());
        assertTrue(events[1].hasRilRequest());
        assertEquals(TelephonyCallSession.Event.RilRequest.RIL_REQUEST_HANGUP,
                events[1].getRilRequest());
        calls = events[1].calls;
        assertTrue(calls[0].hasIndex());
        assertEquals(3, calls[0].getIndex());
        assertEquals(CallState.CALL_DISCONNECTED, calls[0].getState());
    }

    // Test write RIL setup data call
    @Test
    @SmallTest
    public void testWriteRilSetupDataCall() throws Exception {
        mMetrics.writeRilSetupDataCall(
                mPhone.getPhoneId(), 1, 14, 3, "apn", 0, "IPV4V6");

        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        assertTrue(log.events[0].hasType());
        assertEquals(TelephonyEvent.Type.DATA_CALL_SETUP, log.events[0].getType());

        TelephonyEvent.RilSetupDataCall setupDataCall = log.events[0].setupDataCall;
        assertTrue(setupDataCall.hasApn());
        assertEquals("apn", setupDataCall.getApn());
        assertTrue(setupDataCall.hasRat());
        assertEquals(14, setupDataCall.getRat());
        assertTrue(setupDataCall.hasDataProfile());
        assertEquals(4, setupDataCall.getDataProfile());
        assertTrue(setupDataCall.hasType());
        assertEquals(PDP_TYPE_IPV4V6, setupDataCall.getType());
    }

    // Test write service state changed
    @Test
    @SmallTest
    public void testWriteServiceStateChanged() throws Exception {
        mMetrics.writeServiceStateChanged(mPhone.getPhoneId(), mServiceState);
        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        TelephonyEvent event = log.events[0];
        assertTrue(event.hasType());
        assertEquals(TelephonyEvent.Type.RIL_SERVICE_STATE_CHANGED, event.getType());

        TelephonyServiceState state = event.serviceState;
        assertTrue(state.hasVoiceRat());
        assertEquals(RadioAccessTechnology.RAT_LTE, state.getVoiceRat());
        assertTrue(state.hasDataRat());
        assertEquals(RadioAccessTechnology.RAT_LTE, state.getDataRat());
        assertTrue(state.hasVoiceRoamingType());
        assertEquals(RoamingType.ROAMING_TYPE_DOMESTIC, state.getVoiceRoamingType());
        assertTrue(state.hasDataRoamingType());
        assertEquals(RoamingType.ROAMING_TYPE_DOMESTIC, state.getDataRoamingType());
        assertTrue(state.voiceOperator.hasAlphaLong());
        assertEquals("voicelong", state.voiceOperator.getAlphaLong());
        assertTrue(state.voiceOperator.hasAlphaShort());
        assertEquals("voiceshort", state.voiceOperator.getAlphaShort());
        assertTrue(state.voiceOperator.hasNumeric());
        assertEquals("123456", state.voiceOperator.getNumeric());
        assertTrue(state.dataOperator.hasAlphaLong());
        assertEquals("datalong", state.dataOperator.getAlphaLong());
        assertTrue(state.dataOperator.hasAlphaShort());
        assertEquals("datashort", state.dataOperator.getAlphaShort());
        assertTrue(state.dataOperator.hasNumeric());
        assertEquals("123456", state.dataOperator.getNumeric());
    }

    // Test reset scenario
    @Test
    @SmallTest
    public void testReset() throws Exception {
        mMetrics.writeServiceStateChanged(mPhone.getPhoneId(), mServiceState);
        reset();
        TelephonyLog log = buildProto();

        assertEquals(1, log.events.length);
        assertEquals(0, log.callSessions.length);
        assertEquals(0, log.smsSessions.length);
        assertTrue(log.hasEventsDropped());
        assertFalse(log.getEventsDropped());

        TelephonyEvent event = log.events[0];
        assertTrue(event.hasType());
        assertEquals(TelephonyEvent.Type.RIL_SERVICE_STATE_CHANGED, event.getType());

        TelephonyServiceState state = event.serviceState;
        assertTrue(state.hasVoiceRat());
        assertEquals(RadioAccessTechnology.RAT_LTE, state.getVoiceRat());
        assertTrue(state.hasDataRat());
        assertEquals(RadioAccessTechnology.RAT_LTE, state.getDataRat());
        assertTrue(state.hasVoiceRoamingType());
        assertEquals(RoamingType.ROAMING_TYPE_DOMESTIC, state.getVoiceRoamingType());
        assertTrue(state.hasDataRoamingType());
        assertEquals(RoamingType.ROAMING_TYPE_DOMESTIC, state.getDataRoamingType());
        assertTrue(state.voiceOperator.hasAlphaLong());
        assertEquals("voicelong", state.voiceOperator.getAlphaLong());
        assertTrue(state.voiceOperator.hasAlphaShort());
        assertEquals("voiceshort", state.voiceOperator.getAlphaShort());
        assertTrue(state.voiceOperator.hasNumeric());
        assertEquals("123456", state.voiceOperator.getNumeric());
        assertTrue(state.dataOperator.hasAlphaLong());
        assertEquals("datalong", state.dataOperator.getAlphaLong());
        assertTrue(state.dataOperator.hasAlphaShort());
        assertEquals("datashort", state.dataOperator.getAlphaShort());
        assertTrue(state.dataOperator.hasNumeric());
        assertEquals("123456", state.dataOperator.getNumeric());
    }

    // Test Proto Encoding/Decoding
    @Test
    @SmallTest
    public void testProtoEncodingDecoding() throws Exception {
        mMetrics.writeServiceStateChanged(mPhone.getPhoneId(), mServiceState);
        TelephonyLog log = buildProto();
        String encodedString = convertProtoToBase64String(log);

        byte[] decodedString = Base64.decode(encodedString, Base64.DEFAULT);
        assertArrayEquals(TelephonyProto.TelephonyLog.toByteArray(log), decodedString);
    }
}
