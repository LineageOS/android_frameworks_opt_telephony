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

package com.android.internal.telephony.euicc;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.euicc.EuiccProfileInfo;
import android.telephony.euicc.EuiccCardManager;
import android.telephony.euicc.EuiccNotification;
import android.telephony.euicc.EuiccRulesAuthTable;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccSlot;
import com.android.internal.telephony.uicc.euicc.EuiccCard;
import com.android.internal.telephony.uicc.euicc.EuiccCardErrorException;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Backing implementation of {@link EuiccCardManager}. */
public class EuiccCardController extends IEuiccCardController.Stub {
    private static final String TAG = "EuiccCardController";

    private final Context mContext;
    private AppOpsManager mAppOps;
    private String mCallingPackage;
    private ComponentInfo mBestComponent;

    private Handler mEuiccMainThreadHandler;

    private static EuiccCardController sInstance;

    /** Initialize the instance. Should only be called once. */
    public static EuiccCardController init(Context context) {
        synchronized (EuiccCardController.class) {
            if (sInstance == null) {
                sInstance = new EuiccCardController(context);
            } else {
                Log.wtf(TAG, "init() called multiple times! sInstance = " + sInstance);
            }
        }
        return sInstance;
    }

    /** Get an instance. Assumes one has already been initialized with {@link #init}. */
    public static EuiccCardController get() {
        if (sInstance == null) {
            synchronized (EuiccCardController.class) {
                if (sInstance == null) {
                    throw new IllegalStateException("get() called before init()");
                }
            }
        }
        return sInstance;
    }

    private EuiccCardController(Context context) {
        mContext = context;
        mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        mEuiccMainThreadHandler = new Handler();

        ServiceManager.addService("euicc_card_controller", this);
    }

    private void checkCallingPackage(String callingPackage) {
        // Check the caller is LPA.
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        mCallingPackage = callingPackage;
        mBestComponent = EuiccConnector.findBestComponent(mContext.getPackageManager());
        if (mBestComponent == null
                || !TextUtils.equals(mCallingPackage, mBestComponent.packageName)) {
            throw new SecurityException("The calling package can only be LPA.");
        }
    }

    private EuiccCard getEuiccCard(String cardId) {
        UiccController controller = UiccController.getInstance();
        int slotId = controller.getUiccSlotForCardId(cardId);
        if (slotId != UiccController.INVALID_SLOT_ID) {
            UiccSlot slot = controller.getUiccSlot(slotId);
            if (slot.isEuicc()) {
                return (EuiccCard) controller.getUiccCardForSlot(slotId);
            }
        }
        return null;
    }

    private int getResultCode(Throwable e) {
        if (e instanceof EuiccCardErrorException) {
            return ((EuiccCardErrorException) e).getErrorCode();
        }
        return EuiccCardManager.RESULT_UNKNOWN_ERROR;
    }

    @Override
    public void getAllProfiles(String callingPackage, String cardId,
            IGetAllProfilesCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<EuiccProfileInfo[]> cardCb =
                new AsyncResultCallback<EuiccProfileInfo[]>() {
            @Override
            public void onResult(EuiccProfileInfo[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getAllProfiles(cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void getProfile(String callingPackage, String cardId, String iccid,
            IGetProfileCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<EuiccProfileInfo> cardCb = new AsyncResultCallback<EuiccProfileInfo>() {
                    @Override
                    public void onResult(EuiccProfileInfo result) {
                        try {
                            callback.onComplete(EuiccCardManager.RESULT_OK, result);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }

                    @Override
                    public void onException(Throwable e) {
                        try {
                            callback.onComplete(getResultCode(e), null);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }
                };
        getEuiccCard(cardId).getProfile(iccid, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void disableProfile(String callingPackage, String cardId, String iccid, boolean refresh,
            IDisableProfileCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<Void> cardCb = new AsyncResultCallback<Void>() {
            @Override
            public void onResult(Void result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e));
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).disableProfile(iccid, refresh, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void switchToProfile(String callingPackage, String cardId, String iccid, boolean refresh,
            ISwitchToProfileCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<EuiccProfileInfo> profileCb =
                new AsyncResultCallback<EuiccProfileInfo>() {
            @Override
            public void onResult(EuiccProfileInfo profile) {
                AsyncResultCallback<Void> switchCb = new AsyncResultCallback<Void>() {
                    @Override
                    public void onResult(Void result) {
                        try {
                            callback.onComplete(EuiccCardManager.RESULT_OK, profile);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }

                    @Override
                    public void onException(Throwable e) {
                        try {
                            callback.onComplete(getResultCode(e), profile);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }
                };
                getEuiccCard(cardId)
                        .switchToProfile(iccid, refresh, switchCb, mEuiccMainThreadHandler);
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getProfile(iccid, profileCb, mEuiccMainThreadHandler);
    }

    @Override
    public void setNickname(String callingPackage, String cardId, String iccid, String nickname,
            ISetNicknameCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<Void> cardCb = new AsyncResultCallback<Void>() {
            @Override
            public void onResult(Void result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e));
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).setNickname(iccid, nickname, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void deleteProfile(String callingPackage, String cardId, String iccid,
            IDeleteProfileCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<Void> cardCb = new AsyncResultCallback<Void>() {
            @Override
            public void onResult(Void result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e));
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).deleteProfile(iccid, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void resetMemory(String callingPackage, String cardId,
            @EuiccCardManager.ResetOption int options, IResetMemoryCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<Void> cardCb = new AsyncResultCallback<Void>() {
            @Override
            public void onResult(Void result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e));
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).resetMemory(options, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void getDefaultSmdpAddress(String callingPackage, String cardId,
            IGetDefaultSmdpAddressCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<String> cardCb = new AsyncResultCallback<String>() {
            @Override
            public void onResult(String result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getDefaultSmdpAddress(cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void getSmdsAddress(String callingPackage, String cardId,
            IGetSmdsAddressCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<String> cardCb = new AsyncResultCallback<String>() {
            @Override
            public void onResult(String result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getSmdsAddress(cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void setDefaultSmdpAddress(String callingPackage, String cardId, String address,
            ISetDefaultSmdpAddressCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<Void> cardCb = new AsyncResultCallback<Void>() {
            @Override
            public void onResult(Void result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e));
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).setDefaultSmdpAddress(address, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void getRulesAuthTable(String callingPackage, String cardId,
            IGetRulesAuthTableCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<EuiccRulesAuthTable> cardCb =
                new AsyncResultCallback<EuiccRulesAuthTable>() {
            @Override
            public void onResult(EuiccRulesAuthTable result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getRulesAuthTable(cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void getEuiccChallenge(String callingPackage, String cardId,
            IGetEuiccChallengeCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<byte[]> cardCb = new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getEuiccChallenge(cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void getEuiccInfo1(String callingPackage, String cardId,
            IGetEuiccInfo1Callback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<byte[]> cardCb = new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getEuiccInfo1(cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void getEuiccInfo2(String callingPackage, String cardId,
            IGetEuiccInfo2Callback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<byte[]> cardCb = new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).getEuiccInfo2(cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void authenticateServer(String callingPackage, String cardId, String matchingId,
            byte[] serverSigned1, byte[] serverSignature1, byte[] euiccCiPkIdToBeUsed,
            byte[] serverCertificate, IAuthenticateServerCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<byte[]> cardCb = new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).authenticateServer(matchingId, serverSigned1, serverSignature1,
                euiccCiPkIdToBeUsed, serverCertificate, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void prepareDownload(String callingPackage, String cardId, @Nullable byte[] hashCc,
            byte[] smdpSigned2, byte[] smdpSignature2, byte[] smdpCertificate,
            IPrepareDownloadCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<byte[]> cardCb = new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).prepareDownload(hashCc, smdpSigned2, smdpSignature2, smdpCertificate,
                cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void loadBoundProfilePackage(String callingPackage, String cardId,
            byte[] boundProfilePackage, ILoadBoundProfilePackageCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<byte[]> cardCb = new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).loadBoundProfilePackage(boundProfilePackage, cardCb,
                mEuiccMainThreadHandler);
    }

    @Override
    public void cancelSession(String callingPackage, String cardId, byte[] transactionId,
            @EuiccCardManager.CancelReason int reason, ICancelSessionCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<byte[]> cardCb = new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).cancelSession(transactionId, reason, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void listNotifications(String callingPackage, String cardId,
            @EuiccNotification.Event int events, IListNotificationsCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<EuiccNotification[]> cardCb =
                new AsyncResultCallback<EuiccNotification[]>() {
            @Override
            public void onResult(EuiccNotification[] result) {
                try {
                    callback.onComplete(EuiccCardManager.RESULT_OK, result);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }

            @Override
            public void onException(Throwable e) {
                try {
                    callback.onComplete(getResultCode(e), null);
                } catch (RemoteException exception) {
                    throw exception.rethrowFromSystemServer();
                }
            }
        };
        getEuiccCard(cardId).listNotifications(events, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void retrieveNotificationList(String callingPackage, String cardId,
            @EuiccNotification.Event int events, IRetrieveNotificationListCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<EuiccNotification[]> cardCb =
                new AsyncResultCallback<EuiccNotification[]>() {
                    @Override
                    public void onResult(EuiccNotification[] result) {
                        try {
                            callback.onComplete(EuiccCardManager.RESULT_OK, result);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }

                    @Override
                    public void onException(Throwable e) {
                        try {
                            callback.onComplete(getResultCode(e), null);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }
                };
        getEuiccCard(cardId).retrieveNotificationList(events, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void retrieveNotification(String callingPackage, String cardId, int seqNumber,
            IRetrieveNotificationCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<EuiccNotification> cardCb =
                new AsyncResultCallback<EuiccNotification>() {
                    @Override
                    public void onResult(EuiccNotification result) {
                        try {
                            callback.onComplete(EuiccCardManager.RESULT_OK, result);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }

                    @Override
                    public void onException(Throwable e) {
                        try {
                            callback.onComplete(getResultCode(e), null);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }
                };
        getEuiccCard(cardId).retrieveNotification(seqNumber, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void removeNotificationFromList(String callingPackage, String cardId, int seqNumber,
            IRemoveNotificationFromListCallback callback) {
        checkCallingPackage(callingPackage);

        AsyncResultCallback<Void> cardCb = new AsyncResultCallback<Void>() {
                    @Override
                    public void onResult(Void result) {
                        try {
                            callback.onComplete(EuiccCardManager.RESULT_OK);
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }

                    }

                    @Override
                    public void onException(Throwable e) {
                        try {
                            callback.onComplete(getResultCode(e));
                        } catch (RemoteException exception) {
                            throw exception.rethrowFromSystemServer();
                        }
                    }
                };
        getEuiccCard(cardId).removeNotificationFromList(seqNumber, cardCb, mEuiccMainThreadHandler);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, "Requires DUMP");
        final long token = Binder.clearCallingIdentity();

        super.dump(fd, pw, args);
        // TODO(b/38206971): dump more information.
        pw.println("mCallingPackage=" + mCallingPackage);
        pw.println("mBestComponent=" + mBestComponent);

        Binder.restoreCallingIdentity(token);
    }
}
