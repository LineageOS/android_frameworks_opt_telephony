/* Copyright (c) 2018, The Linux Foundation. All rights reserved.
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
 *
 */
package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.HandlerThread;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.android.internal.telephony.cat.*;
import com.android.internal.telephony.TelephonyTestUtils;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccProfile;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import junit.framework.Assert;

public class CatServiceTest extends TelephonyTest {

    private static final int SINGLE_SIM = 1;

    private TelephonyManager mTelephonyManager;
    private CatService mCatService;

    @Mock
    private UiccProfile mMockUiccProfile;

    @Mock
    private IccFileHandler mIccFileHandler;


    /* CatService extends handler and to instantiate the object within this test,
     * we will need a looper to be ready. The HandlerThread here is used for this. The .start()
     * is invoked in the setUp() call and the CatService is initialized in the
     * onLooperPrepared() callback.
     *
     * This will not be required if the class under test is not extending Handler
     */
    private class CatServiceTestHandler extends HandlerThread {

        private CatServiceTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mCatService = CatService.getInstance(
                mSimulatedCommands, mContext, mMockUiccProfile, 0);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(
            Context.TELEPHONY_SERVICE);

        /* These are preconditions for the tests, all the APIs that the Class Under Test
         * will invoke, has to be setup here using dummy values
         */
        doReturn(SINGLE_SIM).when(mTelephonyManager).getSimCount();
        doReturn(SINGLE_SIM).when(mTelephonyManager).getPhoneCount();

        /* Some of the objects are already provided by the base class and a subset of it
         * which is required for the CatService test will be used as dummy responses
         * when the Class Under Test will invoke it in its constructor or in the methods
         * under test
         */
        doReturn(mUiccCardApplication3gpp).when(mMockUiccProfile).getApplicationIndex(0);
        doReturn(mIccFileHandler).when(mUiccCardApplication3gpp).getIccFileHandler();
        doReturn(mSimRecords).when(mUiccCardApplication3gpp).getIccRecords();

        /* Kick off the handler thread, which leads to instantiation of the CatService object */
        new CatServiceTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mTelephonyManager = null;
        mCatService.dispose();
    }

    @Test @SmallTest
    public void isSupportedSetupEventCommand() {
        assertNotNull(mCatService);

        /* Create a mock object to be sent to method under test */
        CatCmdMessage mockCatMsg = mock(CatCmdMessage.class);

        /* Create a real object that will returned when getSetEventList() is invoked
         * on the mock object
         */
        CatCmdMessage.SetupEventListSettings eventListSettings =
            mockCatMsg.new SetupEventListSettings();

        /* Define the mock behavior for getSetEventList(), to return the real object created */
        Mockito.when(mockCatMsg.getSetEventList()).thenReturn(eventListSettings);

        eventListSettings.eventList =
             new int[]{CatCmdMessage.SetupEventListConstants.LANGUAGE_SELECTION_EVENT};
        assertEquals(true, callIsSupportedSetupEventCommand(mockCatMsg));
    }

    /* Wrapper function that uses reflection to invoke private methods */
    private boolean callIsSupportedSetupEventCommand(CatCmdMessage mockCatMsg) {
        Class clsParams[] = new Class[1];
        clsParams[0] = CatCmdMessage.class;

        Object params[] = new Object[1];
        params[0] = mockCatMsg;

        return (boolean)invokeNonStaticMethod(CatService.class, mCatService,
                "isSupportedSetupEventCommand", clsParams, params);
    }

    /* In this test the method under test creates an intent,
     * sets a flag and broadcasts it, this test ensures the flag is set
     */
    @Test
    public void broadcastCatCmdIntent() {
        CatCmdMessage mockCatMsg = mock(CatCmdMessage.class);

        Class clsParams[] = new Class[1];
        clsParams[0] = CatCmdMessage.class;

        Object params[] = new Object[1];
        params[0] = mockCatMsg;

        /* broadcastCatCmdIntent method will get tested and 'sendBroadcast' would get invoked */
        invokeNonStaticMethod(CatService.class, mCatService,
            "broadcastCatCmdIntent", clsParams, params);

        /* Since mock Context is used, sendBroadcast method is trapped
         * and arguments can be examined. In the example below, first argument is an
         * intent and is captured and examined for the flag. Parameters of no interest can
         * be left as anyInt() or anyString() based on the method signature
         */
        ArgumentCaptor<Intent> intentCapture = ArgumentCaptor.forClass(Intent.class);
        Mockito.verify(mContext).sendBroadcast(intentCapture.capture(), Mockito.anyString());

        assertEquals(
            ((Intent)intentCapture.getValue()).getFlags() & Intent.FLAG_RECEIVER_FOREGROUND,
            Intent.FLAG_RECEIVER_FOREGROUND);
    }

    @Test
    public void handleSessionEnd() {
        invokeNonStaticMethod(CatService.class, mCatService, "handleSessionEnd", null, null);

        ArgumentCaptor<Intent> intentCapture = ArgumentCaptor.forClass(Intent.class);
        Mockito.verify(mContext).sendBroadcast(intentCapture.capture(), Mockito.anyString());

        assertEquals(
            ((Intent)intentCapture.getValue()).getFlags() & Intent.FLAG_RECEIVER_FOREGROUND,
            Intent.FLAG_RECEIVER_FOREGROUND);
    }

    private Object invokeNonStaticMethod(Class clazz, Object caller, String method,
                                               Class[] clsParams, Object[] params) {
        try {
            Method methodReflection = clazz.getDeclaredMethod(method, clsParams);
            methodReflection.setAccessible(true);
            return methodReflection.invoke(caller, params);
        } catch (Exception e) {
            Assert.fail(e.toString());
            return null;
        }
    }
}
