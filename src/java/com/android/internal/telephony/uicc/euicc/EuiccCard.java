/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.telephony.uicc.euicc;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.service.carrier.CarrierIdentifier;
import android.service.euicc.EuiccProfileInfo;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.EuiccCardManager;
import android.telephony.euicc.EuiccNotification;
import android.telephony.euicc.EuiccRulesAuthTable;
import android.text.TextUtils;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.asn1.Asn1Decoder;
import com.android.internal.telephony.uicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.asn1.InvalidAsn1DataException;
import com.android.internal.telephony.uicc.asn1.TagNotFoundException;
import com.android.internal.telephony.uicc.euicc.apdu.ApduException;
import com.android.internal.telephony.uicc.euicc.apdu.ApduSender;
import com.android.internal.telephony.uicc.euicc.apdu.RequestBuilder;
import com.android.internal.telephony.uicc.euicc.apdu.RequestProvider;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultHelper;

import java.util.List;

/**
 * This represents an eUICC card to perform profile management operations asynchronously. This class
 * includes methods defined by different versions of GSMA Spec (SGP.22).
 */
public class EuiccCard extends UiccCard {
    private static final String LOG_TAG = "EuiccCard";
    private static final boolean DBG = true;

    private static final String ISD_R_AID = "A0000005591010FFFFFFFF8900000100";
    private static final int ICCID_LENGTH = 20;

    // APDU status for SIM refresh
    private static final int APDU_ERROR_SIM_REFRESH = 0x6F00;

    // These error codes are defined in GSMA SGP.22. 0 is the code for success.
    private static final int CODE_OK = 0;

    // Error code for profile not in expected state for the operation. This error includes the case
    // that profile is not in disabled state when being enabled or deleted, and that profile is not
    // in enabled state when being disabled.
    private static final int CODE_PROFILE_NOT_IN_EXPECTED_STATE = 2;

    private static final EuiccSpecVersion SGP_2_0 = new EuiccSpecVersion(2, 0, 0);

    // These interfaces are used for simplifying the code by leveraging lambdas.
    private interface ApduRequestBuilder {
        void build(RequestBuilder requestBuilder)
                throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException;
    }

    private interface ApduResponseHandler<T> {
        T handleResult(byte[] response)
                throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException;
    }

    private interface ApduExceptionHandler {
        void handleException(Throwable e);
    }

    private final ApduSender mApduSender;
    private final Object mLock = new Object();
    private EuiccSpecVersion mSpecVersion;

    public EuiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        super(c, ci, ics, phoneId);
        // TODO: Set supportExtendedApdu based on ATR.
        mApduSender = new ApduSender(ci, ISD_R_AID, false /* supportExtendedApdu */);
    }

    /**
     * Gets the GSMA RSP specification version supported by this eUICC. This may return null if the
     * version cannot be read.
     */
    public void getSpecVersion(AsyncResultCallback<EuiccSpecVersion> callback, Handler handler) {
        if (mSpecVersion != null) {
            AsyncResultHelper.returnResult(mSpecVersion, callback, handler);
            return;
        }

        sendApdu(newRequestProvider((RequestBuilder requestBuilder) -> { /* Do nothing */ }),
                (byte[] response) -> mSpecVersion, callback, handler);
    }

    /**
     * Gets a list of user-visible profiles.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getAllProfiles(AsyncResultCallback<EuiccProfileInfo[]> callback, Handler handler) {
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_GET_PROFILES)
                                .addChildAsBytes(Tags.TAG_TAG_LIST, Tags.EUICC_PROFILE_TAGS)
                                .build().toHex())),
                (byte[] response) -> {
                    List<Asn1Node> profileNodes = new Asn1Decoder(response).nextNode()
                            .getChild(Tags.TAG_CTX_COMP_0).getChildren(Tags.TAG_PROFILE_INFO);
                    int size = profileNodes.size();
                    EuiccProfileInfo[] profiles = new EuiccProfileInfo[size];
                    int profileCount = 0;
                    for (int i = 0; i < size; i++) {
                        Asn1Node profileNode = profileNodes.get(i);
                        if (!profileNode.hasChild(Tags.TAG_ICCID)) {
                            loge("Profile must have an ICCID.");
                            continue;
                        }
                        EuiccProfileInfo.Builder profileBuilder = new EuiccProfileInfo.Builder();
                        buildProfile(profileNode, profileBuilder);

                        EuiccProfileInfo profile = profileBuilder.build();
                        profiles[profileCount++] = profile;
                    }
                    return profiles;
                },
                callback, handler);
    }

    /**
     * Gets a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void getProfile(String iccid, AsyncResultCallback<EuiccProfileInfo> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Disables a profile of the given {@code iccid}.
     *
     * @param refresh Whether sending the REFRESH command to modem.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void disableProfile(String iccid, boolean refresh, AsyncResultCallback<Void> callback,
            Handler handler) {
        sendApduWithSimResetErrorWorkaround(
                newRequestProvider((RequestBuilder requestBuilder) -> {
                    byte[] iccidBytes = IccUtils.bcdToBytes(padTrailingFs(iccid));
                    requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_DISABLE_PROFILE)
                            .addChild(Asn1Node.newBuilder(Tags.TAG_CTX_COMP_0)
                                    .addChildAsBytes(Tags.TAG_ICCID, iccidBytes))
                            .addChildAsBoolean(Tags.TAG_CTX_1, refresh)
                            .build().toHex());
                }),
                (byte[] response) -> {
                    int result;
                    // SGP.22 v2.0 DisableProfileResponse
                    result = parseSimpleResult(response);
                    switch (result) {
                        case CODE_OK:
                            return null;
                        case CODE_PROFILE_NOT_IN_EXPECTED_STATE:
                            logd("Profile is already disabled, iccid: "
                                    + SubscriptionInfo.givePrintableIccid(iccid));
                            return null;
                        default:
                            throw new EuiccCardErrorException(
                                    EuiccCardErrorException.OPERATION_DISABLE_PROFILE, result);
                    }
                },
                callback, handler);
    }

    /**
     * Switches from the current profile to another profile. The current profile will be disabled
     * and the specified profile will be enabled.
     *
     * @param refresh Whether sending the REFRESH command to modem.
     * @param callback The callback to get the EuiccProfile enabled.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void switchToProfile(String iccid, boolean refresh, AsyncResultCallback<Void> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the EID of the eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getEid(AsyncResultCallback<String> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Sets the nickname of a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void setNickname(String iccid, String nickname, AsyncResultCallback<Void> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Deletes a profile from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void deleteProfile(String iccid, AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Resets the eUICC memory (e.g., remove all profiles).
     *
     * @param options Bits of the options of resetting which parts of the eUICC memory.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void resetMemory(@EuiccCardManager.ResetOption int options,
            AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getDefaultSmdpAddress(AsyncResultCallback<String> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getSmdsAddress(AsyncResultCallback<String> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Sets the default SM-DP+ address of eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void setDefaultSmdpAddress(String defaultSmdpAddress, AsyncResultCallback<Void> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets Rules Authorisation Table.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getRulesAuthTable(AsyncResultCallback<EuiccRulesAuthTable> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the eUICC challenge for new profile downloading.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getEuiccChallenge(AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the eUICC info1 for new profile downloading.
     *
     * @param callback The callback to get the result, which represents an {@code EUICCInfo1}
     *     defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getEuiccInfo1(AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the eUICC info2 for new profile downloading.
     *
     * @param callback The callback to get the result, which represents an {@code EUICCInfo2}
     *     defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getEuiccInfo2(AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Authenticates the SM-DP+ server by the eUICC. The parameters {@code serverSigned1}, {@code
     * serverSignature1}, {@code euiccCiPkIdToBeUsed}, and {@code serverCertificate} are the ASN.1
     * data returned by SM-DP+ server.
     *
     * @param matchingId The activation code or an empty string.
     * @param callback The callback to get the result, which represents an {@code
     *     AuthenticateServerResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void authenticateServer(String matchingId, byte[] serverSigned1, byte[] serverSignature1,
            byte[] euiccCiPkIdToBeUsed, byte[] serverCertificate,
            AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Prepares the profile download request sent to SM-DP+. The parameters {@code smdpSigned2},
     * {@code smdpSignature2}, and {@code smdpCertificate} are the ASN.1 data returned by SM-DP+
     * server.
     *
     * @param hashCc The hash of confirmation code. It can be null if there is no confirmation code
     *     required.
     * @param callback The callback to get the result, which represents an {@code
     *     PrepareDownloadResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void prepareDownload(@Nullable byte[] hashCc, byte[] smdpSigned2, byte[] smdpSignature2,
            byte[] smdpCertificate, AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Loads a downloaded bound profile package onto the eUICC.
     *
     * @param boundProfilePackage The Bound Profile Package data returned by SM-DP+ server.
     * @param callback The callback to get the result, which represents an {@code
     *     LoadBoundProfilePackageResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void loadBoundProfilePackage(byte[] boundProfilePackage,
            AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Cancels the current profile download session.
     *
     * @param transactionId The transaction ID returned by SM-DP+ server.
     * @param callback The callback to get the result, which represents an {@code
     *     CancelSessionResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void cancelSession(byte[] transactionId, @EuiccCardManager.CancelReason int reason,
            AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Lists all notifications of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link EuiccNotification.Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void listNotifications(@EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Retrieves contents of all notification of the given {@code events}.
     *
     * @param events Bits of the event types ({@link EuiccNotification.Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void retrieveNotificationList(@EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Retrieves the content of a notification of the given {@code seqNumber}.
     *
     * @param seqNumber The sequence number of the notification.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void retrieveNotification(int seqNumber, AsyncResultCallback<EuiccNotification> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Removes a notification from eUICC.
     *
     * @param seqNumber The sequence number of the notification.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void removeNotificationFromList(int seqNumber, AsyncResultCallback<Void> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    private RequestProvider newRequestProvider(ApduRequestBuilder builder) {
        return (selectResponse, requestBuilder) -> {
            EuiccSpecVersion ver = getOrExtractSpecVersion(selectResponse);
            if (ver == null) {
                throw new EuiccCardException("Cannot get eUICC spec version.");
            }
            try {
                if (ver.compareTo(SGP_2_0) < 0) {
                    throw new EuiccCardException("eUICC spec version is unsupported: " + ver);
                }
                builder.build(requestBuilder);
            } catch (InvalidAsn1DataException | TagNotFoundException e) {
                throw new EuiccCardException("Cannot parse ASN1 to build request.", e);
            }
        };
    }

    private EuiccSpecVersion getOrExtractSpecVersion(byte[] selectResponse) {
        // Uses the cached version.
        if (mSpecVersion != null) {
            return mSpecVersion;
        }
        // Parses and caches the version.
        EuiccSpecVersion ver = EuiccSpecVersion.fromOpenChannelResponse(selectResponse);
        if (ver != null) {
            synchronized (mLock) {
                if (mSpecVersion == null) {
                    mSpecVersion = ver;
                }
            }
        }
        return ver;
    }

    /**
     * A wrapper on {@link ApduSender#send(RequestProvider, AsyncResultCallback, Handler)} to
     * leverage lambda to simplify the sending APDU code.EuiccCardErrorException.
     *
     * @param requestBuilder Builds the request of APDU commands.
     * @param responseHandler Converts the APDU response from bytes to expected result.
     * @param <T> Type of the originally expected result.
     */
    private <T> void sendApdu(RequestProvider requestBuilder,
            ApduResponseHandler<T> responseHandler, AsyncResultCallback<T> callback,
            Handler handler) {
        sendApdu(requestBuilder, responseHandler,
                (e) -> callback.onException(new EuiccCardException("Cannot send APDU.", e)),
                callback, handler);
    }

    /**
     * This is a workaround solution to the bug that a SIM refresh may interrupt the modem to return
     * the reset of responses of the original APDU command. This applies to disable profile, switch
     * profile, and reset eUICC memory.
     *
     * <p>TODO: Use
     * {@link #sendApdu(RequestProvider, ApduResponseHandler, AsyncResultCallback, Handler)} when
     * this workaround is not needed.
     */
    private void sendApduWithSimResetErrorWorkaround(
            RequestProvider requestBuilder, ApduResponseHandler<Void> responseHandler,
            AsyncResultCallback<Void> callback, Handler handler) {
        sendApdu(requestBuilder, responseHandler, (e) -> {
            if (e instanceof ApduException
                    && ((ApduException) e).getApduStatus() == APDU_ERROR_SIM_REFRESH) {
                logi("Sim is refreshed after disabling profile, no response got.");
                callback.onResult(null);
            } else {
                callback.onException(new EuiccCardException("Cannot send APDU.", e));
            }
        }, callback, handler);
    }

    private <T> void sendApdu(RequestProvider requestBuilder,
            ApduResponseHandler<T> responseHandler,
            ApduExceptionHandler exceptionHandler, AsyncResultCallback<T> callback,
            Handler handler) {
        mApduSender.send(requestBuilder, new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] response) {
                try {
                    callback.onResult(responseHandler.handleResult(response));
                } catch (EuiccCardException e) {
                    callback.onException(e);
                } catch (InvalidAsn1DataException | TagNotFoundException e) {
                    callback.onException(new EuiccCardException(
                            "Cannot parse response: " + IccUtils.bytesToHexString(response), e));
                }
            }

            @Override
            public void onException(Throwable e) {
                exceptionHandler.handleException(e);
            }
        }, handler);
    }

    private static void buildProfile(Asn1Node profileNode, EuiccProfileInfo.Builder profileBuilder)
            throws TagNotFoundException, InvalidAsn1DataException {
        String strippedIccIdString =
                stripTrailingFs(profileNode.getChild(Tags.TAG_ICCID).asBytes());
        profileBuilder.setIccid(strippedIccIdString);

        if (profileNode.hasChild(Tags.TAG_NICKNAME)) {
            profileBuilder.setNickname(profileNode.getChild(Tags.TAG_NICKNAME).asString());
        }

        if (profileNode.hasChild(Tags.TAG_SERVICE_PROVIDER_NAME)) {
            profileBuilder.setServiceProviderName(
                    profileNode.getChild(Tags.TAG_SERVICE_PROVIDER_NAME).asString());
        }

        if (profileNode.hasChild(Tags.TAG_PROFILE_NAME)) {
            profileBuilder.setProfileName(
                    profileNode.getChild(Tags.TAG_PROFILE_NAME).asString());
        }

        if (profileNode.hasChild(Tags.TAG_OPERATOR_ID)) {
            profileBuilder.setCarrierIdentifier(
                    buildCarrierIdentifier(profileNode.getChild(Tags.TAG_OPERATOR_ID)));
        }

        if (profileNode.hasChild(Tags.TAG_PROFILE_STATE)) {
            // noinspection WrongConstant
            profileBuilder.setState(profileNode.getChild(Tags.TAG_PROFILE_STATE).asInteger());
        } else {
            profileBuilder.setState(EuiccProfileInfo.PROFILE_STATE_DISABLED);
        }

        if (profileNode.hasChild(Tags.TAG_PROFILE_CLASS)) {
            // noinspection WrongConstant
            profileBuilder.setProfileClass(
                    profileNode.getChild(Tags.TAG_PROFILE_CLASS).asInteger());
        } else {
            profileBuilder.setProfileClass(EuiccProfileInfo.PROFILE_CLASS_OPERATIONAL);
        }

        if (profileNode.hasChild(Tags.TAG_PROFILE_POLICY_RULE)) {
            // noinspection WrongConstant
            profileBuilder.setPolicyRules(
                    profileNode.getChild(Tags.TAG_PROFILE_POLICY_RULE).asBits());
        }

        if (profileNode.hasChild(Tags.TAG_CARRIER_PRIVILEGE_RULES)) {
            List<Asn1Node> refArDoNodes = profileNode.getChild(Tags.TAG_CARRIER_PRIVILEGE_RULES)
                    .getChildren(Tags.TAG_REF_AR_DO);
            profileBuilder.setUiccAccessRule(buildUiccAccessRule(refArDoNodes));
        }
    }

    private static CarrierIdentifier buildCarrierIdentifier(Asn1Node node)
            throws InvalidAsn1DataException, TagNotFoundException {
        String gid1 = null;
        if (node.hasChild(Tags.TAG_CTX_1)) {
            gid1 = IccUtils.bytesToHexString(node.getChild(Tags.TAG_CTX_1).asBytes());
        }
        String gid2 = null;
        if (node.hasChild(Tags.TAG_CTX_2)) {
            gid2 = IccUtils.bytesToHexString(node.getChild(Tags.TAG_CTX_2).asBytes());
        }
        return new CarrierIdentifier(node.getChild(Tags.TAG_CTX_0).asBytes(), gid1, gid2);
    }

    @Nullable
    private static UiccAccessRule[] buildUiccAccessRule(List<Asn1Node> nodes)
            throws InvalidAsn1DataException, TagNotFoundException {
        if (nodes.isEmpty()) {
            return null;
        }
        int count = nodes.size();
        UiccAccessRule[] rules = new UiccAccessRule[count];
        for (int i = 0; i < count; i++) {
            Asn1Node node = nodes.get(i);
            Asn1Node refDoNode = node.getChild(Tags.TAG_REF_DO);
            byte[] signature = refDoNode.getChild(Tags.TAG_DEVICE_APP_ID_REF_DO).asBytes();

            String packageName = null;
            if (refDoNode.hasChild(Tags.TAG_PKG_REF_DO)) {
                packageName = refDoNode.getChild(Tags.TAG_PKG_REF_DO).asString();
            }
            long accessType = 0;
            if (node.hasChild(Tags.TAG_AR_DO, Tags.TAG_PERM_AR_DO)) {
                Asn1Node permArDoNode = node.getChild(Tags.TAG_AR_DO, Tags.TAG_PERM_AR_DO);
                accessType = permArDoNode.asRawLong();
            }
            rules[i] = new UiccAccessRule(signature, packageName, accessType);
        }
        return rules;
    }

    /** Returns the first CONTEXT [0] as an integer. */
    private static int parseSimpleResult(byte[] response)
            throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException {
        return parseResponse(response).getChild(Tags.TAG_CTX_0).asInteger();
    }

    private static Asn1Node parseResponse(byte[] response)
            throws EuiccCardException, InvalidAsn1DataException {
        Asn1Decoder decoder = new Asn1Decoder(response);
        if (!decoder.hasNextNode()) {
            throw new EuiccCardException("Empty response", null);
        }
        return decoder.nextNode();
    }

    /** Strip all the trailing 'F' characters of an iccId. */
    private static String stripTrailingFs(byte[] iccId) {
        return IccUtils.stripTrailingFs(IccUtils.bchToString(iccId, 0, iccId.length));
    }

    /** Pad an iccId with trailing 'F' characters until the length is 20. */
    private static String padTrailingFs(String iccId) {
        if (!TextUtils.isEmpty(iccId) && iccId.length() < ICCID_LENGTH) {
            iccId += new String(new char[20 - iccId.length()]).replace('\0', 'F');
        }
        return iccId;
    }

    private static void loge(String message) {
        Rlog.e(LOG_TAG, message);
    }

    private static void logi(String message) {
        Rlog.i(LOG_TAG, message);
    }

    private static void logd(String message) {
        if (DBG) {
            Rlog.d(LOG_TAG, message);
        }
    }
}
