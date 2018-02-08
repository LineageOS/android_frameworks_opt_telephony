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

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.asn1.Asn1Decoder;
import com.android.internal.telephony.uicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.asn1.InvalidAsn1DataException;
import com.android.internal.telephony.uicc.asn1.TagNotFoundException;
import com.android.internal.telephony.uicc.euicc.EuiccCardErrorException.OperationCode;
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

    // Error code for nothing to delete when resetting eUICC memory or removing notifications.
    private static final int CODE_NOTHING_TO_DELETE = 1;

    // Error code for no result available when retrieving notifications.
    private static final int CODE_NO_RESULT_AVAILABLE = 1;

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
    private String mEid;

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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_GET_PROFILES)
                                .addChildAsBytes(Tags.TAG_ICCID, IccUtils.bcdToBytes(iccid))
                                .addChildAsBytes(Tags.TAG_TAG_LIST, Tags.EUICC_PROFILE_TAGS)
                                .build().toHex())),
                (byte[] response) -> {
                    List<Asn1Node> profileNodes = new Asn1Decoder(response).nextNode()
                            .getChild(Tags.TAG_CTX_COMP_0).getChildren(Tags.TAG_PROFILE_INFO);
                    if (profileNodes.isEmpty()) {
                        return null;
                    }
                    Asn1Node profileNode = profileNodes.get(0);
                    EuiccProfileInfo.Builder profileBuilder = new EuiccProfileInfo.Builder();
                    buildProfile(profileNode, profileBuilder);
                    return profileBuilder.build();
                },
                callback, handler);
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
        sendApduWithSimResetErrorWorkaround(
                newRequestProvider((RequestBuilder requestBuilder) -> {
                    byte[] iccidBytes = IccUtils.bcdToBytes(padTrailingFs(iccid));
                    requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_ENABLE_PROFILE)
                            .addChild(Asn1Node.newBuilder(Tags.TAG_CTX_COMP_0)
                                    .addChildAsBytes(Tags.TAG_ICCID, iccidBytes))
                            .addChildAsBoolean(Tags.TAG_CTX_1, refresh)
                            .build().toHex());
                }),
                (byte[] response) -> {
                    int result;
                    // SGP.22 v2.0 EnableProfileResponse
                    result = parseSimpleResult(response);
                    switch (result) {
                        case CODE_OK:
                            return null;
                        case CODE_PROFILE_NOT_IN_EXPECTED_STATE:
                            logd("Profile is already enabled, iccid: "
                                    + SubscriptionInfo.givePrintableIccid(iccid));
                            return null;
                        default:
                            throw new EuiccCardErrorException(
                                    EuiccCardErrorException.OPERATION_SWITCH_TO_PROFILE, result);
                    }
                },
                callback, handler);
    }

    /**
     * Gets the EID of the eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getEid(AsyncResultCallback<String> callback, Handler handler) {
        if (mEid != null) {
            AsyncResultHelper.returnResult(mEid, callback, handler);
            return;
        }
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_GET_EID)
                                .addChildAsBytes(Tags.TAG_TAG_LIST, new byte[] {Tags.TAG_EID})
                                .build().toHex())),
                (byte[] response) -> {
                    String eid = IccUtils.bytesToHexString(parseResponse(response)
                            .getChild(Tags.TAG_EID).asBytes());
                    synchronized (mLock) {
                        mEid = eid;
                    }
                    return eid;
                },
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_SET_NICKNAME)
                                .addChildAsBytes(Tags.TAG_ICCID,
                                        IccUtils.bcdToBytes(padTrailingFs(iccid)))
                                .addChildAsString(Tags.TAG_NICKNAME, nickname)
                                .build().toHex())),
                (byte[] response) -> {
                    // SGP.22 v2.0 SetNicknameResponse
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK) {
                        throw new EuiccCardErrorException(
                                EuiccCardErrorException.OPERATION_SET_NICKNAME, result);
                    }
                    return null;
                },
                callback, handler);
    }

    /**
     * Deletes a profile from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void deleteProfile(String iccid, AsyncResultCallback<Void> callback, Handler handler) {
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) -> {
                    byte[] iccidBytes = IccUtils.bcdToBytes(padTrailingFs(iccid));
                    requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_DELETE_PROFILE)
                            .addChildAsBytes(Tags.TAG_ICCID, iccidBytes)
                            .build().toHex());
                }),
                (byte[] response) -> {
                    // SGP.22 v2.0 DeleteProfileRequest
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK) {
                        throw new EuiccCardErrorException(
                                EuiccCardErrorException.OPERATION_DELETE_PROFILE, result);
                    }
                    return null;
                },
                callback, handler);
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
        sendApduWithSimResetErrorWorkaround(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_EUICC_MEMORY_RESET)
                                .addChildAsBits(Tags.TAG_CTX_2, options)
                                .build().toHex())),
                (byte[] response) -> {
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK && result != CODE_NOTHING_TO_DELETE) {
                        throw new EuiccCardErrorException(
                                EuiccCardErrorException.OPERATION_RESET_MEMORY, result);
                    }
                    return null;
                },
                callback, handler);
    }

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getDefaultSmdpAddress(AsyncResultCallback<String> callback, Handler handler) {
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(
                                Asn1Node.newBuilder(Tags.TAG_GET_CONFIGURED_ADDRESSES)
                                        .build().toHex())),
                (byte[] response) -> parseResponse(response).getChild(Tags.TAG_CTX_0).asString(),
                callback, handler);
    }

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getSmdsAddress(AsyncResultCallback<String> callback, Handler handler) {
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(
                                Asn1Node.newBuilder(Tags.TAG_GET_CONFIGURED_ADDRESSES)
                                        .build().toHex())),
                (byte[] response) -> parseResponse(response).getChild(Tags.TAG_CTX_1).asString(),
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(
                                Asn1Node.newBuilder(Tags.TAG_SET_DEFAULT_SMDP_ADDRESS)
                                        .addChildAsString(Tags.TAG_CTX_0, defaultSmdpAddress)
                                        .build().toHex())),
                (byte[] response) -> {
                    // SGP.22 v2.0 SetDefaultDpAddressResponse
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK) {
                        throw new EuiccCardErrorException(
                                EuiccCardErrorException.OPERATION_SET_DEFAULT_SMDP_ADDRESS, result);
                    }
                    return null;
                },
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_GET_RAT)
                                .build().toHex())),
                (byte[] response) -> {
                    Asn1Node root = parseResponse(response);
                    List<Asn1Node> nodes = root.getChildren(Tags.TAG_CTX_COMP_0);
                    EuiccRulesAuthTable.Builder builder =
                            new EuiccRulesAuthTable.Builder(nodes.size());
                    int size = nodes.size();
                    for (int i = 0; i < size; i++) {
                        Asn1Node node = nodes.get(i);
                        List<Asn1Node> opIdNodes = node.getChild(Tags.TAG_CTX_COMP_1).getChildren();
                        int opIdSize = opIdNodes.size();
                        CarrierIdentifier[] opIds = new CarrierIdentifier[opIdSize];
                        for (int j = 0; j < opIdSize; j++) {
                            opIds[j] = buildCarrierIdentifier(opIdNodes.get(j));
                        }
                        builder.add(node.getChild(Tags.TAG_CTX_0).asBits(), opIds,
                                node.getChild(Tags.TAG_CTX_2).asBits());
                    }
                    return builder.build();
                },
                callback, handler);
    }

    /**
     * Gets the eUICC challenge for new profile downloading.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getEuiccChallenge(AsyncResultCallback<byte[]> callback, Handler handler) {
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(
                                Asn1Node.newBuilder(Tags.TAG_GET_EUICC_CHALLENGE)
                                        .build().toHex())),
                (byte[] response) -> parseResponse(response).getChild(Tags.TAG_CTX_0).asBytes(),
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_GET_EUICC_INFO_1)
                                .build().toHex())),
                (response) -> response,
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_GET_EUICC_INFO_2)
                                .build().toHex())),
                (response) -> response,
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) -> {
                    byte[] imeiBytes = getDeviceId();
                    // TAC is the first 8 digits (4 bytes) of IMEI.
                    byte[] tacBytes = new byte[4];
                    System.arraycopy(imeiBytes, 0, tacBytes, 0, 4);

                    // TODO: Get device capabilities.
                    Asn1Node.Builder devCapsBuilder = Asn1Node.newBuilder(Tags.TAG_CTX_COMP_1);

                    Asn1Node.Builder ctxParams1Builder = Asn1Node.newBuilder(Tags.TAG_CTX_COMP_0)
                            .addChildAsString(Tags.TAG_CTX_0, matchingId)
                            .addChild(Asn1Node.newBuilder(Tags.TAG_CTX_COMP_1)
                                    .addChildAsBytes(Tags.TAG_CTX_0, tacBytes)
                                    .addChild(devCapsBuilder)
                                    .addChildAsBytes(Tags.TAG_CTX_2, imeiBytes));

                    requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_AUTHENTICATE_SERVER)
                            .addChild(new Asn1Decoder(serverSigned1).nextNode())
                            .addChild(new Asn1Decoder(serverSignature1).nextNode())
                            .addChild(new Asn1Decoder(euiccCiPkIdToBeUsed).nextNode())
                            .addChild(new Asn1Decoder(serverCertificate).nextNode())
                            .addChild(ctxParams1Builder)
                            .build().toHex());
                }),
                (byte[] response) ->
                        parseResponseAndCheckSimpleError(response,
                                EuiccCardErrorException.OPERATION_AUTHENTICATE_SERVER).toBytes(),
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) -> {
                    Asn1Node.Builder builder = Asn1Node.newBuilder(Tags.TAG_PREPARE_DOWNLOAD)
                            .addChild(new Asn1Decoder(smdpSigned2).nextNode())
                            .addChild(new Asn1Decoder(smdpSignature2).nextNode());
                    if (hashCc != null) {
                        builder.addChildAsBytes(Tags.TAG_UNI_4, hashCc);
                    }
                    requestBuilder.addStoreData(
                            builder.addChild(new Asn1Decoder(smdpCertificate).nextNode())
                                    .build().toHex());
                }),
                (byte[] response) -> {
                    Asn1Node root = parseResponse(response);
                    if (root.hasChild(Tags.TAG_CTX_COMP_1, Tags.TAG_UNI_2)) {
                        throw new EuiccCardErrorException(
                                EuiccCardErrorException.OPERATION_PREPARE_DOWNLOAD,
                                root.getChild(Tags.TAG_CTX_COMP_1, Tags.TAG_UNI_2).asInteger());
                    }
                    return root.toBytes();
                },
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) -> {
                    Asn1Node bppNode = new Asn1Decoder(boundProfilePackage).nextNode();
                    // initialiseSecureChannelRequest (ES8+.InitialiseSecureChannel)
                    Asn1Node initialiseSecureChannelRequest = bppNode.getChild(
                            Tags.TAG_INITIALISE_SECURE_CHANNEL);
                    // firstSequenceOf87 (ES8+.ConfigureISDP)
                    Asn1Node firstSequenceOf87 = bppNode.getChild(Tags.TAG_CTX_COMP_0);
                    // sequenceOf88 (ES8+.StoreMetadata)
                    Asn1Node sequenceOf88 = bppNode.getChild(Tags.TAG_CTX_COMP_1);
                    List<Asn1Node> metaDataSeqs = sequenceOf88.getChildren(Tags.TAG_CTX_8);
                    // sequenceOf86 (ES8+.LoadProfileElements #1)
                    Asn1Node sequenceOf86 = bppNode.getChild(Tags.TAG_CTX_COMP_3);
                    List<Asn1Node> elementSeqs = sequenceOf86.getChildren(Tags.TAG_CTX_6);

                    requestBuilder.addStoreData(bppNode.getHeadAsHex()
                            + initialiseSecureChannelRequest.toHex());

                    requestBuilder.addStoreData(firstSequenceOf87.toHex());

                    requestBuilder.addStoreData(sequenceOf88.getHeadAsHex());
                    int size = metaDataSeqs.size();
                    for (int i = 0; i < size; i++) {
                        requestBuilder.addStoreData(metaDataSeqs.get(i).toHex());
                    }

                    if (bppNode.hasChild(Tags.TAG_CTX_COMP_2)) {
                        requestBuilder.addStoreData(bppNode.getChild(Tags.TAG_CTX_COMP_2).toHex());
                    }

                    requestBuilder.addStoreData(sequenceOf86.getHeadAsHex());
                    size = elementSeqs.size();
                    for (int i = 0; i < size; i++) {
                        requestBuilder.addStoreData(elementSeqs.get(i).toHex());
                    }
                }),
                (byte[] response) -> {
                    // SGP.22 v2.0 ErrorResult
                    Asn1Node root = parseResponse(response);
                    if (root.hasChild(Tags.TAG_PROFILE_INSTALLATION_RESULT_DATA,
                            Tags.TAG_CTX_COMP_2, Tags.TAG_CTX_COMP_1, Tags.TAG_CTX_1)) {
                        Asn1Node errorNode = root.getChild(
                                Tags.TAG_PROFILE_INSTALLATION_RESULT_DATA, Tags.TAG_CTX_COMP_2,
                                Tags.TAG_CTX_COMP_1, Tags.TAG_CTX_1);
                        throw new EuiccCardErrorException(
                                EuiccCardErrorException.OPERATION_LOAD_BOUND_PROFILE_PACKAGE,
                                errorNode.asInteger(), errorNode);
                    }
                    return root.toBytes();
                },
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_CANCEL_SESSION)
                                .addChildAsBytes(Tags.TAG_CTX_0, transactionId)
                                .addChildAsInteger(Tags.TAG_CTX_1, reason)
                                .build().toHex())),
                (byte[] response) ->
                        parseResponseAndCheckSimpleError(response,
                                EuiccCardErrorException.OPERATION_CANCEL_SESSION).toBytes(),
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(Asn1Node.newBuilder(Tags.TAG_LIST_NOTIFICATION)
                                .addChildAsBits(Tags.TAG_CTX_1, events)
                                .build().toHex())),
                (byte[] response) -> {
                    Asn1Node root = parseResponseAndCheckSimpleError(response,
                            EuiccCardErrorException.OPERATION_LIST_NOTIFICATIONS);
                    List<Asn1Node> nodes = root.getChild(Tags.TAG_CTX_COMP_0).getChildren();
                    EuiccNotification[] notifications = new EuiccNotification[nodes.size()];
                    for (int i = 0; i < notifications.length; ++i) {
                        notifications[i] = createNotification(nodes.get(i));
                    }
                    return notifications;
                },
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(
                                Asn1Node.newBuilder(Tags.TAG_RETRIEVE_NOTIFICATIONS_LIST)
                                        .addChild(Asn1Node.newBuilder(Tags.TAG_CTX_COMP_0)
                                                .addChildAsBits(Tags.TAG_CTX_1, events))
                                        .build().toHex())),
                (byte[] response) -> {
                    Asn1Node root = parseResponse(response);
                    if (root.hasChild(Tags.TAG_CTX_1)) {
                        // SGP.22 v2.0 RetrieveNotificationsListResponse
                        int error = root.getChild(Tags.TAG_CTX_1).asInteger();
                        switch (error) {
                            case CODE_NO_RESULT_AVAILABLE:
                                return new EuiccNotification[0];
                            default:
                                throw new EuiccCardErrorException(
                                        EuiccCardErrorException.OPERATION_RETRIEVE_NOTIFICATION,
                                        error);
                        }
                    }
                    List<Asn1Node> nodes = root.getChild(Tags.TAG_CTX_COMP_0).getChildren();
                    EuiccNotification[] notifications = new EuiccNotification[nodes.size()];
                    for (int i = 0; i < notifications.length; ++i) {
                        notifications[i] = createNotification(nodes.get(i));
                    }
                    return notifications;
                },
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(
                                Asn1Node.newBuilder(Tags.TAG_RETRIEVE_NOTIFICATIONS_LIST)
                                        .addChild(Asn1Node.newBuilder(Tags.TAG_CTX_COMP_0)
                                                .addChildAsInteger(Tags.TAG_CTX_0, seqNumber))
                                        .build().toHex())),
                (byte[] response) -> {
                    Asn1Node root = parseResponseAndCheckSimpleError(response,
                            EuiccCardErrorException.OPERATION_RETRIEVE_NOTIFICATION);
                    List<Asn1Node> nodes = root.getChild(Tags.TAG_CTX_COMP_0).getChildren();
                    if (nodes.size() > 0) {
                        return createNotification(nodes.get(0));
                    }
                    return null;
                },
                callback, handler);
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
        sendApdu(
                newRequestProvider((RequestBuilder requestBuilder) ->
                        requestBuilder.addStoreData(
                                Asn1Node.newBuilder(Tags.TAG_REMOVE_NOTIFICATION_FROM_LIST)
                                        .addChildAsInteger(Tags.TAG_CTX_0, seqNumber)
                                        .build().toHex())),
                (byte[] response) -> {
                    // SGP.22 v2.0 NotificationSentResponse
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK && result != CODE_NOTHING_TO_DELETE) {
                        throw new EuiccCardErrorException(
                                EuiccCardErrorException.OPERATION_REMOVE_NOTIFICATION_FROM_LIST,
                                result);
                    }
                    return null;
                },
                callback, handler);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected byte[] getDeviceId() {
        byte[] imeiBytes = new byte[8];
        Phone phone = PhoneFactory.getPhone(getPhoneId());
        if (phone != null) {
            IccUtils.bcdToBytes(phone.getDeviceId(), imeiBytes);
        }
        return imeiBytes;
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

    /**
     * Creates an instance from the ASN.1 data.
     *
     * @param node This should be either {@code NotificationMetadata} or {@code PendingNotification}
     *     defined by SGP.22 v2.0.
     * @throws TagNotFoundException If no notification tag is found in the bytes.
     * @throws InvalidAsn1DataException If no valid data is found in the bytes.
     */
    private static EuiccNotification createNotification(Asn1Node node)
            throws TagNotFoundException, InvalidAsn1DataException {
        Asn1Node metadataNode;
        if (node.getTag() == Tags.TAG_NOTIFICATION_METADATA) {
            metadataNode = node;
        } else if (node.getTag() == Tags.TAG_PROFILE_INSTALLATION_RESULT) {
            metadataNode = node.getChild(Tags.TAG_PROFILE_INSTALLATION_RESULT_DATA,
                    Tags.TAG_NOTIFICATION_METADATA);
        } else {
            // Other signed notification
            metadataNode = node.getChild(Tags.TAG_NOTIFICATION_METADATA);
        }
        // noinspection WrongConstant
        return new EuiccNotification(metadataNode.getChild(Tags.TAG_SEQ).asInteger(),
                metadataNode.getChild(Tags.TAG_TARGET_ADDR).asString(),
                metadataNode.getChild(Tags.TAG_EVENT).asBits(),
                node.getTag() == Tags.TAG_NOTIFICATION_METADATA ? null : node.toBytes());
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

    /**
     * Parses the bytes into an ASN1 node and check if there is an error code represented at the
     * context 1 tag. If there is an error code, an {@link EuiccCardErrorException} will be thrown
     * with the given operation code.
     */
    private static Asn1Node parseResponseAndCheckSimpleError(byte[] response,
            @OperationCode int opCode)
            throws EuiccCardException, InvalidAsn1DataException, TagNotFoundException {
        Asn1Node root = parseResponse(response);
        if (root.hasChild(Tags.TAG_CTX_1)) {
            throw new EuiccCardErrorException(opCode, root.getChild(Tags.TAG_CTX_1).asInteger());
        }
        return root;
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
