/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ALLOCATE_PDU_SESSION_ID;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ALLOW_DATA;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ANSWER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_BASEBAND_VERSION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CANCEL_HANDOVER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CANCEL_USSD;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_BROADCAST_ACTIVATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_BURST_DTMF;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_FLASH;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SEND_SMS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SEND_SMS_EXPECT_MORE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_SUBSCRIPTION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CHANGE_BARRING_PASSWORD;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CHANGE_SIM_PIN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CHANGE_SIM_PIN2;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_CONFERENCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DATA_CALL_LIST;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DATA_REGISTRATION_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DEACTIVATE_DATA_CALL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DELETE_SMS_ON_SIM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DEVICE_IDENTITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DIAL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DTMF;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DTMF_START;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_DTMF_STOP;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_EMERGENCY_DIAL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENABLE_MODEM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENABLE_NR_DUAL_CONNECTIVITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENABLE_UICC_APPLICATIONS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_DEPERSONALIZATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PIN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PIN2;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PUK;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ENTER_SIM_PUK2;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_EXPLICIT_CALL_TRANSFER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_ACTIVITY_INFO;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_ALLOWED_CARRIERS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_ALLOWED_NETWORK_TYPES_BITMAP;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_BARRING_INFO;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_CELL_INFO_LIST;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_CLIR;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_CURRENT_CALLS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_DC_RT_INFO;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_HAL_DEVICE_CAPABILITIES;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_HARDWARE_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_IMEI;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_IMEISV;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_IMSI;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_MODEM_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_MUTE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_NEIGHBORING_CELL_IDS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_PHONE_CAPABILITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_RADIO_CAPABILITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SIM_PHONEBOOK_CAPACITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SIM_PHONEBOOK_RECORDS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SIM_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SLICING_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SLOT_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SMSC_ADDRESS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SYSTEM_SELECTION_CHANNELS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_UICC_APPLICATIONS_ENABLEMENT;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GSM_BROADCAST_ACTIVATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GSM_GET_BROADCAST_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GSM_SET_BROADCAST_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_HANGUP;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_IMS_REGISTRATION_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_IMS_SEND_SMS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_ISIM_AUTHENTICATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_IS_NR_DUAL_CONNECTIVITY_ENABLED;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_LAST_CALL_FAIL_CAUSE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_NV_READ_ITEM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_NV_RESET_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_NV_WRITE_CDMA_PRL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_NV_WRITE_ITEM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_OEM_HOOK_RAW;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_OEM_HOOK_STRINGS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_OPERATOR;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_PULL_LCEDATA;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_AVAILABLE_NETWORKS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_CALL_FORWARD_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_CALL_WAITING;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_CLIP;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_FACILITY_LOCK;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_QUERY_TTY_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RELEASE_PDU_SESSION_ID;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_REPORT_SMS_MEMORY_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RESET_RADIO;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SCREEN_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_DEVICE_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_SMS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_SMS_EXPECT_MORE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEND_USSD;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SEPARATE_CONNECTION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SETUP_DATA_CALL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_ALLOWED_CARRIERS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_ALLOWED_NETWORK_TYPES_BITMAP;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_BAND_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_CALL_FORWARD;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_CALL_WAITING;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_CARRIER_INFO_IMSI_ENCRYPTION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_CLIR;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_DATA_PROFILE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_DATA_THROTTLING;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_DC_RT_INFO_RATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_FACILITY_LOCK;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_INITIAL_ATTACH_APN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_LINK_CAPACITY_REPORTING_CRITERIA;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_LOCATION_UPDATES;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_MUTE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_PREFERRED_DATA_MODEM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_RADIO_CAPABILITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_SIGNAL_STRENGTH_REPORTING_CRITERIA;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_SIM_CARD_POWER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_SMSC_ADDRESS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_SYSTEM_SELECTION_CHANNELS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_TTY_MODE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_UICC_SUBSCRIPTION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SHUTDOWN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIGNAL_STRENGTH;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_AUTHENTICATION;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_CLOSE_CHANNEL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_IO;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_OPEN_CHANNEL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SMS_ACKNOWLEDGE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_START_HANDOVER;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_START_KEEPALIVE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_START_LCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_START_NETWORK_SCAN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STK_GET_PROFILE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STK_SET_PROFILE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STOP_KEEPALIVE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STOP_LCE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_STOP_NETWORK_SCAN;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SWITCH_DUAL_SIM_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_UDUB;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_UPDATE_SIM_PHONEBOOK_RECORD;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_VOICE_RADIO_TECH;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_VOICE_REGISTRATION_STATE;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_WRITE_SMS_TO_SIM;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_BARRING_INFO_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CALL_RING;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CARRIER_INFO_IMSI_ENCRYPTION;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CDMA_CALL_WAITING;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CDMA_INFO_REC;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CDMA_OTA_PROVISION_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CDMA_PRL_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CELL_INFO_LIST;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_DATA_CALL_LIST_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_DC_RT_INFO_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_EMERGENCY_NUMBER_LIST;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_HARDWARE_CONFIG_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_ICC_SLOT_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_KEEPALIVE_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_LCEDATA_RECV;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_MODEM_RESTART;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_NETWORK_SCAN_RESULT;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_NITZ_TIME_RECEIVED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_OEM_HOOK_RAW;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_ON_SS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_ON_USSD;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_ON_USSD_REQUEST;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_PCO_DATA;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_PHYSICAL_CHANNEL_CONFIG;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RADIO_CAPABILITY;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_REGISTRATION_FAILED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESEND_INCALL_MUTE;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_CDMA_NEW_SMS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NEW_SMS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_SIM_PHONEBOOK_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_SIM_PHONEBOOK_RECORDS_RECEIVED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESTRICTED_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RIL_CONNECTED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RINGBACK_TONE;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SIGNAL_STRENGTH;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SIM_REFRESH;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SIM_SMS_STORAGE_FULL;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SRVCC_STATE_NOTIFY;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_CALL_SETUP;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_CC_ALPHA_NOTIFY;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_EVENT_NOTIFY;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_PROACTIVE_COMMAND;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_SESSION_END;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SUPP_SVC_NOTIFICATION;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_UICC_APPLICATIONS_ENABLEMENT_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_UNTHROTTLE_APN;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_VOICE_RADIO_TECH_CHANGED;

import android.annotation.Nullable;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.os.SystemClock;
import android.service.carrier.CarrierIdentifier;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation;
import android.telephony.CellInfo;
import android.telephony.LinkCapacityEstimate;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhysicalChannelConfig;
import android.telephony.RadioAccessSpecifier;
import android.telephony.ServiceState;
import android.telephony.SignalThresholdInfo;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.EpsQos;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.data.NrQos;
import android.telephony.data.Qos;
import android.telephony.data.QosBearerFilter;
import android.telephony.data.QosBearerSession;
import android.telephony.data.RouteSelectionDescriptor;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.UrspRule;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.cat.ComprehensionTlv;
import com.android.internal.telephony.cat.ComprehensionTlvTag;
import com.android.internal.telephony.dataconnection.KeepaliveStatus;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.telephony.Rlog;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utils class for HAL <-> RIL conversions
 */
public class RILUtils {
    private static final String LOG_TAG = "RILUtils";

    /**
     * Convert to PersoSubstate defined in radio/1.5/types.hal
     * @param persoType PersoSubState type
     * @return The converted PersoSubstate
     */
    public static int convertToHalPersoType(
            IccCardApplicationStatus.PersoSubState persoType) {
        switch (persoType) {
            case PERSOSUBSTATE_IN_PROGRESS:
                return android.hardware.radio.V1_5.PersoSubstate.IN_PROGRESS;
            case  PERSOSUBSTATE_READY:
                return android.hardware.radio.V1_5.PersoSubstate.READY;
            case PERSOSUBSTATE_SIM_NETWORK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_NETWORK;
            case PERSOSUBSTATE_SIM_NETWORK_SUBSET:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_NETWORK_SUBSET;
            case PERSOSUBSTATE_SIM_CORPORATE:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_CORPORATE;
            case PERSOSUBSTATE_SIM_SERVICE_PROVIDER:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SERVICE_PROVIDER;
            case PERSOSUBSTATE_SIM_SIM:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SIM;
            case PERSOSUBSTATE_SIM_NETWORK_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_NETWORK_PUK;
            case PERSOSUBSTATE_SIM_NETWORK_SUBSET_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_NETWORK_SUBSET_PUK;
            case PERSOSUBSTATE_SIM_CORPORATE_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_CORPORATE_PUK;
            case PERSOSUBSTATE_SIM_SERVICE_PROVIDER_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SERVICE_PROVIDER_PUK;
            case PERSOSUBSTATE_SIM_SIM_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SIM_PUK;
            case PERSOSUBSTATE_RUIM_NETWORK1:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_NETWORK1;
            case PERSOSUBSTATE_RUIM_NETWORK2:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_NETWORK2;
            case PERSOSUBSTATE_RUIM_HRPD:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_HRPD;
            case PERSOSUBSTATE_RUIM_CORPORATE:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_CORPORATE;
            case PERSOSUBSTATE_RUIM_SERVICE_PROVIDER:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_SERVICE_PROVIDER;
            case PERSOSUBSTATE_RUIM_RUIM:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_RUIM;
            case PERSOSUBSTATE_RUIM_NETWORK1_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_NETWORK1_PUK;
            case PERSOSUBSTATE_RUIM_NETWORK2_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_NETWORK2_PUK;
            case PERSOSUBSTATE_RUIM_HRPD_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_HRPD_PUK;
            case PERSOSUBSTATE_RUIM_CORPORATE_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_CORPORATE_PUK;
            case PERSOSUBSTATE_RUIM_SERVICE_PROVIDER_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_SERVICE_PROVIDER_PUK;
            case PERSOSUBSTATE_RUIM_RUIM_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.RUIM_RUIM_PUK;
            case PERSOSUBSTATE_SIM_SPN:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SPN;
            case PERSOSUBSTATE_SIM_SPN_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SPN_PUK;
            case PERSOSUBSTATE_SIM_SP_EHPLMN:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SP_EHPLMN;
            case PERSOSUBSTATE_SIM_SP_EHPLMN_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_SP_EHPLMN_PUK;
            case PERSOSUBSTATE_SIM_ICCID:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_ICCID;
            case PERSOSUBSTATE_SIM_ICCID_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_ICCID_PUK;
            case PERSOSUBSTATE_SIM_IMPI:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_IMPI;
            case PERSOSUBSTATE_SIM_IMPI_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_IMPI_PUK;
            case PERSOSUBSTATE_SIM_NS_SP:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_NS_SP;
            case PERSOSUBSTATE_SIM_NS_SP_PUK:
                return android.hardware.radio.V1_5.PersoSubstate.SIM_NS_SP_PUK;
            default:
                return android.hardware.radio.V1_5.PersoSubstate.UNKNOWN;
        }
    }

    /**
     * Convert to GsmSmsMessage defined in radio/1.0/types.hal
     * @param smscPdu SMSD address
     * @param pdu SMS in PDU format
     * @return A converted GsmSmsMessage
     */
    public static android.hardware.radio.V1_0.GsmSmsMessage convertToHalGsmSmsMessage(
            String smscPdu, String pdu) {
        android.hardware.radio.V1_0.GsmSmsMessage msg =
                new android.hardware.radio.V1_0.GsmSmsMessage();
        msg.smscPdu = smscPdu == null ? "" : smscPdu;
        msg.pdu = pdu == null ? "" : pdu;
        return msg;
    }

    /**
     * Convert to CdmaSmsMessage defined in radio/1.0/types.hal
     * @param pdu SMS in PDU format
     * @return A converted CdmaSmsMessage
     */
    public static android.hardware.radio.V1_0.CdmaSmsMessage convertToHalCdmaSmsMessage(
            byte[] pdu) {
        android.hardware.radio.V1_0.CdmaSmsMessage msg =
                new android.hardware.radio.V1_0.CdmaSmsMessage();
        int addrNbrOfDigits;
        int subaddrNbrOfDigits;
        int bearerDataLength;
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);

        try {
            msg.teleserviceId = dis.readInt(); // teleServiceId
            msg.isServicePresent = (byte) dis.readInt() == 1; // servicePresent
            msg.serviceCategory = dis.readInt(); // serviceCategory
            msg.address.digitMode = dis.read();  // address digit mode
            msg.address.numberMode = dis.read(); // address number mode
            msg.address.numberType = dis.read(); // address number type
            msg.address.numberPlan = dis.read(); // address number plan
            addrNbrOfDigits = (byte) dis.read();
            for (int i = 0; i < addrNbrOfDigits; i++) {
                msg.address.digits.add(dis.readByte()); // address_orig_bytes[i]
            }
            msg.subAddress.subaddressType = dis.read(); //subaddressType
            msg.subAddress.odd = (byte) dis.read() == 1; //subaddr odd
            subaddrNbrOfDigits = (byte) dis.read();
            for (int i = 0; i < subaddrNbrOfDigits; i++) {
                msg.subAddress.digits.add(dis.readByte()); //subaddr_orig_bytes[i]
            }

            bearerDataLength = dis.read();
            for (int i = 0; i < bearerDataLength; i++) {
                msg.bearerData.add(dis.readByte()); //bearerData[i]
            }
        } catch (IOException ex) {
        }
        return msg;
    }

    /**
     * Convert to DataProfileInfo defined in radio/1.0/types.hal
     * @param dp Data profile
     * @return The converted DataProfileInfo
     */
    public static android.hardware.radio.V1_0.DataProfileInfo convertToHalDataProfile10(
            DataProfile dp) {
        android.hardware.radio.V1_0.DataProfileInfo dpi =
                new android.hardware.radio.V1_0.DataProfileInfo();

        dpi.profileId = dp.getProfileId();
        dpi.apn = dp.getApn();
        dpi.protocol = ApnSetting.getProtocolStringFromInt(dp.getProtocolType());
        dpi.roamingProtocol = ApnSetting.getProtocolStringFromInt(dp.getRoamingProtocolType());
        dpi.authType = dp.getAuthType();
        dpi.user = TextUtils.emptyIfNull(dp.getUserName());
        dpi.password = TextUtils.emptyIfNull(dp.getPassword());
        dpi.type = dp.getType();
        dpi.maxConnsTime = dp.getMaxConnectionsTime();
        dpi.maxConns = dp.getMaxConnections();
        dpi.waitTime = dp.getWaitTime();
        dpi.enabled = dp.isEnabled();
        dpi.supportedApnTypesBitmap = dp.getSupportedApnTypesBitmask();
        // Shift by 1 bit due to the discrepancy between
        // android.hardware.radio.V1_0.RadioAccessFamily and the bitmask version of
        // ServiceState.RIL_RADIO_TECHNOLOGY_XXXX.
        dpi.bearerBitmap = ServiceState.convertNetworkTypeBitmaskToBearerBitmask(
                dp.getBearerBitmask()) << 1;
        dpi.mtu = dp.getMtuV4();
        dpi.mvnoType = android.hardware.radio.V1_0.MvnoType.NONE;
        dpi.mvnoMatchData = "";

        return dpi;
    }

    /**
     * Convert to DataProfileInfo defined in radio/1.4/types.hal
     * @param dp Data profile
     * @return The converted DataProfileInfo
     */
    public static android.hardware.radio.V1_4.DataProfileInfo convertToHalDataProfile14(
            DataProfile dp) {
        android.hardware.radio.V1_4.DataProfileInfo dpi =
                new android.hardware.radio.V1_4.DataProfileInfo();

        dpi.apn = dp.getApn();
        dpi.protocol = dp.getProtocolType();
        dpi.roamingProtocol = dp.getRoamingProtocolType();
        dpi.authType = dp.getAuthType();
        dpi.user = TextUtils.emptyIfNull(dp.getUserName());
        dpi.password = TextUtils.emptyIfNull(dp.getPassword());
        dpi.type = dp.getType();
        dpi.maxConnsTime = dp.getMaxConnectionsTime();
        dpi.maxConns = dp.getMaxConnections();
        dpi.waitTime = dp.getWaitTime();
        dpi.enabled = dp.isEnabled();
        dpi.supportedApnTypesBitmap = dp.getSupportedApnTypesBitmask();
        // Shift by 1 bit due to the discrepancy between
        // android.hardware.radio.V1_0.RadioAccessFamily and the bitmask version of
        // ServiceState.RIL_RADIO_TECHNOLOGY_XXXX.
        dpi.bearerBitmap = ServiceState.convertNetworkTypeBitmaskToBearerBitmask(
                dp.getBearerBitmask()) << 1;
        dpi.mtu = dp.getMtuV4();
        dpi.persistent = dp.isPersistent();
        dpi.preferred = dp.isPreferred();

        // profile id is only meaningful when it's persistent on the modem.
        dpi.profileId = (dpi.persistent) ? dp.getProfileId()
                : android.hardware.radio.V1_0.DataProfileId.INVALID;

        return dpi;
    }

    /**
     * Convert to DataProfileInfo defined in radio/1.5/types.hal
     * @param dp Data profile
     * @return The converted DataProfileInfo
     */
    public static android.hardware.radio.V1_5.DataProfileInfo convertToHalDataProfile15(
            DataProfile dp) {
        android.hardware.radio.V1_5.DataProfileInfo dpi =
                new android.hardware.radio.V1_5.DataProfileInfo();

        dpi.apn = dp.getApn();
        dpi.protocol = dp.getProtocolType();
        dpi.roamingProtocol = dp.getRoamingProtocolType();
        dpi.authType = dp.getAuthType();
        dpi.user = TextUtils.emptyIfNull(dp.getUserName());
        dpi.password = TextUtils.emptyIfNull(dp.getPassword());
        dpi.type = dp.getType();
        dpi.maxConnsTime = dp.getMaxConnectionsTime();
        dpi.maxConns = dp.getMaxConnections();
        dpi.waitTime = dp.getWaitTime();
        dpi.enabled = dp.isEnabled();
        dpi.supportedApnTypesBitmap = dp.getSupportedApnTypesBitmask();
        // Shift by 1 bit due to the discrepancy between
        // android.hardware.radio.V1_0.RadioAccessFamily and the bitmask version of
        // ServiceState.RIL_RADIO_TECHNOLOGY_XXXX.
        dpi.bearerBitmap = ServiceState.convertNetworkTypeBitmaskToBearerBitmask(
                dp.getBearerBitmask()) << 1;
        dpi.mtuV4 = dp.getMtuV4();
        dpi.mtuV6 = dp.getMtuV6();
        dpi.persistent = dp.isPersistent();
        dpi.preferred = dp.isPreferred();

        // profile id is only meaningful when it's persistent on the modem.
        dpi.profileId = (dpi.persistent) ? dp.getProfileId()
                : android.hardware.radio.V1_0.DataProfileId.INVALID;

        return dpi;
    }

    /**
     * Convert to OptionalSliceInfo defined in radio/1.6/types.hal
     * @param sliceInfo Slice info
     * @return The converted OptionalSliceInfo
     */
    public static android.hardware.radio.V1_6.OptionalSliceInfo convertToHalSliceInfo(
            @Nullable NetworkSliceInfo sliceInfo) {
        android.hardware.radio.V1_6.OptionalSliceInfo optionalSliceInfo =
                new android.hardware.radio.V1_6.OptionalSliceInfo();
        if (sliceInfo == null) {
            return optionalSliceInfo;
        }

        android.hardware.radio.V1_6.SliceInfo si = new android.hardware.radio.V1_6.SliceInfo();
        si.sst = (byte) sliceInfo.getSliceServiceType();
        si.mappedHplmnSst = (byte) sliceInfo.getMappedHplmnSliceServiceType();
        si.sliceDifferentiator = sliceInfo.getSliceDifferentiator();
        si.mappedHplmnSD = sliceInfo.getMappedHplmnSliceDifferentiator();
        optionalSliceInfo.value(si);
        return optionalSliceInfo;
    }

    /**
     * Convert to OptionalTrafficDescriptor defined in radio/1.6/types.hal
     * @param trafficDescriptor Traffic descriptor
     * @return The converted OptionalTrafficDescriptor
     */
    public static android.hardware.radio.V1_6.OptionalTrafficDescriptor
            convertToHalTrafficDescriptor(@Nullable TrafficDescriptor trafficDescriptor) {
        android.hardware.radio.V1_6.OptionalTrafficDescriptor optionalTrafficDescriptor =
                new android.hardware.radio.V1_6.OptionalTrafficDescriptor();
        if (trafficDescriptor == null) {
            return optionalTrafficDescriptor;
        }

        android.hardware.radio.V1_6.TrafficDescriptor td =
                new android.hardware.radio.V1_6.TrafficDescriptor();

        android.hardware.radio.V1_6.OptionalDnn optionalDnn =
                new android.hardware.radio.V1_6.OptionalDnn();
        if (trafficDescriptor.getDataNetworkName() != null) {
            optionalDnn.value(trafficDescriptor.getDataNetworkName());
        }
        td.dnn = optionalDnn;

        android.hardware.radio.V1_6.OptionalOsAppId optionalOsAppId =
                new android.hardware.radio.V1_6.OptionalOsAppId();
        if (trafficDescriptor.getOsAppId() != null) {
            android.hardware.radio.V1_6.OsAppId osAppId = new android.hardware.radio.V1_6.OsAppId();
            osAppId.osAppId = primitiveArrayToArrayList(trafficDescriptor.getOsAppId());
            optionalOsAppId.value(osAppId);
        }
        td.osAppId = optionalOsAppId;

        optionalTrafficDescriptor.value(td);
        return optionalTrafficDescriptor;
    }

    /**
     * Convert to ResetNvType defined in radio/1.0/types.hal
     * @param resetType NV reset type
     * @return The converted reset type in integer or -1 if param is invalid
     */
    public static int convertToHalResetNvType(int resetType) {
        /**
         * resetType values
         * 1 - reload all NV items
         * 2 - erase NV reset (SCRTN)
         * 3 - factory reset (RTN)
         */
        switch (resetType) {
            case 1: return android.hardware.radio.V1_0.ResetNvType.RELOAD;
            case 2: return android.hardware.radio.V1_0.ResetNvType.ERASE;
            case 3: return android.hardware.radio.V1_0.ResetNvType.FACTORY_RESET;
        }
        return -1;
    }

    /**
     * Convert to a list of LinkAddress defined in radio/1.5/types.hal
     * @param linkProperties Link properties
     * @return The converted list of LinkAddresses
     */
    public static ArrayList<android.hardware.radio.V1_5.LinkAddress> convertToHalLinkProperties15(
            LinkProperties linkProperties) {
        ArrayList<android.hardware.radio.V1_5.LinkAddress> addresses15 = new ArrayList<>();
        if (linkProperties != null) {
            for (android.net.LinkAddress la : linkProperties.getAllLinkAddresses()) {
                android.hardware.radio.V1_5.LinkAddress linkAddress =
                        new android.hardware.radio.V1_5.LinkAddress();
                linkAddress.address = la.getAddress().getHostAddress();
                linkAddress.properties = la.getFlags();
                linkAddress.deprecationTime = la.getDeprecationTime();
                linkAddress.expirationTime = la.getExpirationTime();
                addresses15.add(linkAddress);
            }
        }
        return addresses15;
    }

    /**
     * Convert to RadioAccessSpecifier defined in radio/1.1/types.hal
     * @param ras Radio access specifier
     * @return The converted RadioAccessSpecifier
     */
    public static android.hardware.radio.V1_1.RadioAccessSpecifier
            convertToHalRadioAccessSpecifier11(RadioAccessSpecifier ras) {
        android.hardware.radio.V1_1.RadioAccessSpecifier rasInHalFormat =
                new android.hardware.radio.V1_1.RadioAccessSpecifier();
        rasInHalFormat.radioAccessNetwork = ras.getRadioAccessNetwork();
        ArrayList<Integer> bands = new ArrayList<>();
        if (ras.getBands() != null) {
            for (int band : ras.getBands()) {
                bands.add(band);
            }
        }
        switch (ras.getRadioAccessNetwork()) {
            case AccessNetworkConstants.AccessNetworkType.GERAN:
                rasInHalFormat.geranBands = bands;
                break;
            case AccessNetworkConstants.AccessNetworkType.UTRAN:
                rasInHalFormat.utranBands = bands;
                break;
            case AccessNetworkConstants.AccessNetworkType.EUTRAN:
                rasInHalFormat.eutranBands = bands;
                break;
            default:
                return null;
        }

        if (ras.getChannels() != null) {
            for (int channel : ras.getChannels()) {
                rasInHalFormat.channels.add(channel);
            }
        }

        return rasInHalFormat;
    }

    /**
     * Convert to RadioAccessSpecifier defined in radio/1.5/types.hal
     * @param ras Radio access specifier
     * @return The converted RadioAccessSpecifier
     */
    public static android.hardware.radio.V1_5.RadioAccessSpecifier
            convertToHalRadioAccessSpecifier15(RadioAccessSpecifier ras) {
        android.hardware.radio.V1_5.RadioAccessSpecifier rasInHalFormat =
                new android.hardware.radio.V1_5.RadioAccessSpecifier();
        android.hardware.radio.V1_5.RadioAccessSpecifier.Bands bandsInHalFormat =
                new android.hardware.radio.V1_5.RadioAccessSpecifier.Bands();
        rasInHalFormat.radioAccessNetwork = convertToHalRadioAccessNetworks(
                ras.getRadioAccessNetwork());
        ArrayList<Integer> bands = new ArrayList<>();
        if (ras.getBands() != null) {
            for (int band : ras.getBands()) {
                bands.add(band);
            }
        }
        switch (ras.getRadioAccessNetwork()) {
            case AccessNetworkConstants.AccessNetworkType.GERAN:
                bandsInHalFormat.geranBands(bands);
                break;
            case AccessNetworkConstants.AccessNetworkType.UTRAN:
                bandsInHalFormat.utranBands(bands);
                break;
            case AccessNetworkConstants.AccessNetworkType.EUTRAN:
                bandsInHalFormat.eutranBands(bands);
                break;
            case AccessNetworkConstants.AccessNetworkType.NGRAN:
                bandsInHalFormat.ngranBands(bands);
                break;
            default:
                return null;
        }
        rasInHalFormat.bands = bandsInHalFormat;

        if (ras.getChannels() != null) {
            for (int channel : ras.getChannels()) {
                rasInHalFormat.channels.add(channel);
            }
        }

        return rasInHalFormat;
    }

    /**
     * Convert to censored terminal response
     * @param terminalResponse Terminal response
     * @return The converted censored terminal response
     */
    public static String convertToCensoredTerminalResponse(String terminalResponse) {
        try {
            byte[] bytes = IccUtils.hexStringToBytes(terminalResponse);
            if (bytes != null) {
                List<ComprehensionTlv> ctlvs = ComprehensionTlv.decodeMany(bytes, 0);
                int from = 0;
                for (ComprehensionTlv ctlv : ctlvs) {
                    // Find text strings which might be personal information input by user,
                    // then replace it with "********".
                    if (ComprehensionTlvTag.TEXT_STRING.value() == ctlv.getTag()) {
                        byte[] target = Arrays.copyOfRange(ctlv.getRawValue(), from,
                                ctlv.getValueIndex() + ctlv.getLength());
                        terminalResponse = terminalResponse.toLowerCase().replace(
                                IccUtils.bytesToHexString(target).toLowerCase(), "********");
                    }
                    // The text string tag and the length field should also be hidden.
                    from = ctlv.getValueIndex() + ctlv.getLength();
                }
            }
        } catch (Exception e) {
            terminalResponse = null;
        }

        return terminalResponse;
    }

    /**
     * Convert to {@link TelephonyManager.NetworkTypeBitMask}, the bitmask represented by
     * {@link android.telephony.Annotation.NetworkType}.
     *
     * @param raf {@link android.hardware.radio.V1_0.RadioAccessFamily}
     * @return {@link TelephonyManager.NetworkTypeBitMask}
     */
    @TelephonyManager.NetworkTypeBitMask
    public static int convertHalNetworkTypeBitMask(int raf) {
        int networkTypeRaf = 0;

        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.GSM) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_GSM;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.GPRS) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_GPRS;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.EDGE) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_EDGE;
        }
        // convert both IS95A/IS95B to CDMA as network mode doesn't support CDMA
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.IS95A) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_CDMA;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.IS95B) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_CDMA;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.ONE_X_RTT) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.EVDO_0) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_0;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.EVDO_A) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_A;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.EVDO_B) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_B;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.EHRPD) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_EHRPD;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.HSUPA) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.HSDPA) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.HSPA) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_HSPA;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.HSPAP) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.UMTS) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_UMTS;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.TD_SCDMA) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_TD_SCDMA;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.LTE) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        }
        if ((raf & android.hardware.radio.V1_0.RadioAccessFamily.LTE_CA) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA;
        }
        if ((raf & android.hardware.radio.V1_4.RadioAccessFamily.NR) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_NR;
        }
        // TODO: need hal definition
        if ((raf & (1 << ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN)) != 0) {
            networkTypeRaf |= TelephonyManager.NETWORK_TYPE_BITMASK_IWLAN;
        }
        return (networkTypeRaf == 0) ? TelephonyManager.NETWORK_TYPE_UNKNOWN : networkTypeRaf;
    }

    /**
     * Convert to RadioAccessFamily defined in radio/1.4/types.hal
     * @param networkTypeBitmask {@link TelephonyManager.NetworkTypeBitMask}, the bitmask
     *        represented by {@link android.telephony.Annotation.NetworkType}
     * @return The converted RadioAccessFamily
     */
    public static int convertToHalRadioAccessFamily(
            @TelephonyManager.NetworkTypeBitMask int networkTypeBitmask) {
        int raf = 0;

        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_GSM) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.GSM;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_GPRS) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.GPRS;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_EDGE) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.EDGE;
        }
        // convert CDMA to IS95A, consistent with ServiceState.networkTypeToRilRadioTechnology
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_CDMA) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.IS95A;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.ONE_X_RTT;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_0) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.EVDO_0;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_A) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.EVDO_A;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_EVDO_B) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.EVDO_B;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_EHRPD) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.EHRPD;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.HSUPA;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.HSDPA;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_HSPA) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.HSPA;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_HSPAP) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.HSPAP;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_UMTS) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.UMTS;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_TD_SCDMA) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.TD_SCDMA;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_LTE) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.LTE;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA) != 0) {
            raf |= android.hardware.radio.V1_0.RadioAccessFamily.LTE_CA;
        }
        if ((networkTypeBitmask & TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0) {
            raf |= android.hardware.radio.V1_4.RadioAccessFamily.NR;
        }
        // TODO: need hal definition for IWLAN
        return (raf == 0) ? android.hardware.radio.V1_4.RadioAccessFamily.UNKNOWN : raf;
    }

    /**
     * Convert AccessNetworkType to AccessNetwork defined in radio/1.5/types.hal
     * @param accessNetworkType Access networkt ype
     * @return The converted AccessNetwork
     */
    public static int convertToHalAccessNetwork(int accessNetworkType) {
        switch (accessNetworkType) {
            case AccessNetworkConstants.AccessNetworkType.GERAN:
                return android.hardware.radio.V1_5.AccessNetwork.GERAN;
            case AccessNetworkConstants.AccessNetworkType.UTRAN:
                return android.hardware.radio.V1_5.AccessNetwork.UTRAN;
            case AccessNetworkConstants.AccessNetworkType.EUTRAN:
                return android.hardware.radio.V1_5.AccessNetwork.EUTRAN;
            case AccessNetworkConstants.AccessNetworkType.CDMA2000:
                return android.hardware.radio.V1_5.AccessNetwork.CDMA2000;
            case AccessNetworkConstants.AccessNetworkType.IWLAN:
                return android.hardware.radio.V1_5.AccessNetwork.IWLAN;
            case AccessNetworkConstants.AccessNetworkType.NGRAN:
                return android.hardware.radio.V1_5.AccessNetwork.NGRAN;
            case AccessNetworkConstants.AccessNetworkType.UNKNOWN:
            default:
                return android.hardware.radio.V1_5.AccessNetwork.UNKNOWN;
        }
    }

    /**
     * Convert AccessNetworkType to RadioAccessNetwork defined in radio/1.1/types.hal
     * @param accessNetworkType Access network type
     * @return The converted RadioAccessNetwork
     */
    public static int convertToHalRadioAccessNetworks(int accessNetworkType) {
        switch (accessNetworkType) {
            case AccessNetworkConstants.AccessNetworkType.GERAN:
                return android.hardware.radio.V1_1.RadioAccessNetworks.GERAN;
            case AccessNetworkConstants.AccessNetworkType.UTRAN:
                return android.hardware.radio.V1_1.RadioAccessNetworks.UTRAN;
            case AccessNetworkConstants.AccessNetworkType.EUTRAN:
                return android.hardware.radio.V1_1.RadioAccessNetworks.EUTRAN;
            case AccessNetworkConstants.AccessNetworkType.NGRAN:
                return android.hardware.radio.V1_5.RadioAccessNetworks.NGRAN;
            case AccessNetworkConstants.AccessNetworkType.CDMA2000:
                return android.hardware.radio.V1_5.RadioAccessNetworks.CDMA2000;
            case AccessNetworkConstants.AccessNetworkType.UNKNOWN:
            default:
                return android.hardware.radio.V1_5.RadioAccessNetworks.UNKNOWN;
        }
    }

    /**
     * Convert RadioAccessNetworks defined in radio/1.5/types.hal to AccessNetworkType
     * @param ran RadioAccessNetwork defined in radio/1.5/types.hal
     * @return The converted AccessNetworkType
     */
    public static int convertHalRadioAccessNetworks(int ran) {
        switch (ran) {
            case android.hardware.radio.V1_5.RadioAccessNetworks.GERAN:
                return AccessNetworkConstants.AccessNetworkType.GERAN;
            case android.hardware.radio.V1_5.RadioAccessNetworks.UTRAN:
                return AccessNetworkConstants.AccessNetworkType.UTRAN;
            case android.hardware.radio.V1_5.RadioAccessNetworks.EUTRAN:
                return AccessNetworkConstants.AccessNetworkType.EUTRAN;
            case android.hardware.radio.V1_5.RadioAccessNetworks.NGRAN:
                return AccessNetworkConstants.AccessNetworkType.NGRAN;
            case android.hardware.radio.V1_5.RadioAccessNetworks.CDMA2000:
                return AccessNetworkConstants.AccessNetworkType.CDMA2000;
            case android.hardware.radio.V1_5.RadioAccessNetworks.UNKNOWN:
            default:
                return AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        }
    }

    /**
     * Convert to SimApdu defined in radio/1.0/types.hal
     * @param channel channel
     * @param cla cla
     * @param instruction instruction
     * @param p1 p1
     * @param p2 p2
     * @param p3 p3
     * @param data data
     * @return The converted SimApdu
     */
    public static android.hardware.radio.V1_0.SimApdu convertToHalSimApdu(int channel, int cla,
            int instruction, int p1, int p2, int p3, String data) {
        android.hardware.radio.V1_0.SimApdu msg = new android.hardware.radio.V1_0.SimApdu();
        msg.sessionId = channel;
        msg.cla = cla;
        msg.instruction = instruction;
        msg.p1 = p1;
        msg.p2 = p2;
        msg.p3 = p3;
        msg.data = convertNullToEmptyString(data);
        return msg;
    }

    /**
     * Convert a list of CarrierIdentifiers into a list of Carrier defined in radio/1.0/types.hal
     * @param carriers List of CarrierIdentifiers
     * @return The converted list of Carriers
     */
    public static ArrayList<android.hardware.radio.V1_0.Carrier> convertToHalCarrierRestrictionList(
            List<CarrierIdentifier> carriers) {
        ArrayList<android.hardware.radio.V1_0.Carrier> result = new ArrayList<>();
        for (CarrierIdentifier ci : carriers) {
            android.hardware.radio.V1_0.Carrier c = new android.hardware.radio.V1_0.Carrier();
            c.mcc = convertNullToEmptyString(ci.getMcc());
            c.mnc = convertNullToEmptyString(ci.getMnc());
            int matchType = CarrierIdentifier.MatchType.ALL;
            String matchData = null;
            if (!TextUtils.isEmpty(ci.getSpn())) {
                matchType = CarrierIdentifier.MatchType.SPN;
                matchData = ci.getSpn();
            } else if (!TextUtils.isEmpty(ci.getImsi())) {
                matchType = CarrierIdentifier.MatchType.IMSI_PREFIX;
                matchData = ci.getImsi();
            } else if (!TextUtils.isEmpty(ci.getGid1())) {
                matchType = CarrierIdentifier.MatchType.GID1;
                matchData = ci.getGid1();
            } else if (!TextUtils.isEmpty(ci.getGid2())) {
                matchType = CarrierIdentifier.MatchType.GID2;
                matchData = ci.getGid2();
            }
            c.matchType = matchType;
            c.matchData = convertNullToEmptyString(matchData);
            result.add(c);
        }
        return result;
    }

    /**
     * Convert to SignalThresholdInfo defined in radio/1.5/types.hal
     * @param signalThresholdInfo Signal threshold info
     * @return The converted SignalThresholdInfo
     */
    public static android.hardware.radio.V1_5.SignalThresholdInfo convertToHalSignalThresholdInfo(
            SignalThresholdInfo signalThresholdInfo) {
        android.hardware.radio.V1_5.SignalThresholdInfo signalThresholdInfoHal =
                new android.hardware.radio.V1_5.SignalThresholdInfo();
        signalThresholdInfoHal.signalMeasurement = signalThresholdInfo.getSignalMeasurementType();
        signalThresholdInfoHal.hysteresisMs = signalThresholdInfo.getHysteresisMs();
        signalThresholdInfoHal.hysteresisDb = signalThresholdInfo.getHysteresisDb();
        signalThresholdInfoHal.thresholds = primitiveArrayToArrayList(
                signalThresholdInfo.getThresholds());
        signalThresholdInfoHal.isEnabled = signalThresholdInfo.isEnabled();
        return signalThresholdInfoHal;
    }

    /**
     * Convert StatusOnIcc to SmsWriteArgsStatus defined in radio/1.0/types.hal
     * @param status StatusOnIcc
     * @return The converted SmsWriteArgsStatus defined in radio/1.0/types.hal
     */
    public static int convertToHalSmsWriteArgsStatus(int status) {
        switch(status & 0x7) {
            case SmsManager.STATUS_ON_ICC_READ:
                return android.hardware.radio.V1_0.SmsWriteArgsStatus.REC_READ;
            case SmsManager.STATUS_ON_ICC_UNREAD:
                return android.hardware.radio.V1_0.SmsWriteArgsStatus.REC_UNREAD;
            case SmsManager.STATUS_ON_ICC_SENT:
                return android.hardware.radio.V1_0.SmsWriteArgsStatus.STO_SENT;
            case SmsManager.STATUS_ON_ICC_UNSENT:
                return android.hardware.radio.V1_0.SmsWriteArgsStatus.STO_UNSENT;
            default:
                return android.hardware.radio.V1_0.SmsWriteArgsStatus.REC_READ;
        }
    }

    /**
     * Convert a list of HardwareConfig defined in radio/1.0/types.hal to a list of HardwareConfig
     * @param hwListRil List of HardwareConfig defined in radio/1.0/types.hal
     * @return The converted list of HardwareConfig
     */
    @TelephonyManager.NetworkTypeBitMask
    public static ArrayList<HardwareConfig> convertHalHardwareConfigList(
            ArrayList<android.hardware.radio.V1_0.HardwareConfig> hwListRil) {
        int num;
        ArrayList<HardwareConfig> response;
        HardwareConfig hw;

        num = hwListRil.size();
        response = new ArrayList<>(num);

        for (android.hardware.radio.V1_0.HardwareConfig hwRil : hwListRil) {
            int type = hwRil.type;
            switch(type) {
                case HardwareConfig.DEV_HARDWARE_TYPE_MODEM: {
                    hw = new HardwareConfig(type);
                    android.hardware.radio.V1_0.HardwareConfigModem hwModem = hwRil.modem.get(0);
                    hw.assignModem(hwRil.uuid, hwRil.state, hwModem.rilModel, hwModem.rat,
                            hwModem.maxVoice, hwModem.maxData, hwModem.maxStandby);
                    break;
                }
                case HardwareConfig.DEV_HARDWARE_TYPE_SIM: {
                    hw = new HardwareConfig(type);
                    hw.assignSim(hwRil.uuid, hwRil.state, hwRil.sim.get(0).modemUuid);
                    break;
                }
                default: {
                    throw new RuntimeException(
                            "RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardware type:" + type);
                }
            }
            response.add(hw);
        }
        return response;
    }

    /**
     * Convert RadioCapability defined in radio/1.0/types.hal to RadioCapability
     * @param rc RadioCapability defined in radio/1.0/types.hal
     * @param ril RIL
     * @return The converted RadioCapability
     */
    public static RadioCapability convertHalRadioCapability(
            android.hardware.radio.V1_0.RadioCapability rc, RIL ril) {
        int session = rc.session;
        int phase = rc.phase;
        int rat = convertHalNetworkTypeBitMask(rc.raf);
        String logicModemUuid = rc.logicalModemUuid;
        int status = rc.status;

        ril.riljLog("convertHalRadioCapability: session=" + session + ", phase=" + phase + ", rat="
                + rat + ", logicModemUuid=" + logicModemUuid + ", status=" + status + ", rcRil.raf="
                + rc.raf);
        return new RadioCapability(ril.mPhoneId, session, phase, rat, logicModemUuid, status);
    }

    /**
     * Convert LceDataInfo defined in radio/1.0/types.hal to a list of LinkCapacityEstimates
     * @param lce LceDataInfo defined in radio/1.0/types.hal
     * @return The converted list of LinkCapacityEstimates
     */
    public static List<LinkCapacityEstimate> convertHalLceData(
            android.hardware.radio.V1_0.LceDataInfo lce) {
        final List<LinkCapacityEstimate> lceList = new ArrayList<>();
        lceList.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_COMBINED,
                lce.lastHopCapacityKbps, LinkCapacityEstimate.INVALID));
        return lceList;
    }

    /**
     * Convert LinkCapacityEstimate defined in radio/1.2/types.hal to a list of
     * LinkCapacityEstimates
     * @param lce LinkCapacityEstimate defined in radio/1.2/types.hal
     * @return The converted list of LinkCapacityEstimates
     */
    public static List<LinkCapacityEstimate> convertHalLceData(
            android.hardware.radio.V1_2.LinkCapacityEstimate lce) {
        final List<LinkCapacityEstimate> lceList = new ArrayList<>();
        lceList.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_COMBINED,
                lce.downlinkCapacityKbps, lce.uplinkCapacityKbps));
        return lceList;
    }

    /**
     * Convert LinkCapacityEstimate defined in radio/1.6/types.hal to a list of
     * LinkCapacityEstimates
     * @param lce LinkCapacityEstimate defined in radio/1.6/types.hal
     * @return The converted list of LinkCapacityEstimates
     */
    public static List<LinkCapacityEstimate> convertHalLceData(
            android.hardware.radio.V1_6.LinkCapacityEstimate lce) {
        final List<LinkCapacityEstimate> lceList = new ArrayList<>();
        int primaryDownlinkCapacityKbps = lce.downlinkCapacityKbps;
        int primaryUplinkCapacityKbps = lce.uplinkCapacityKbps;
        if (primaryDownlinkCapacityKbps != LinkCapacityEstimate.INVALID
                && lce.secondaryDownlinkCapacityKbps != LinkCapacityEstimate.INVALID) {
            primaryDownlinkCapacityKbps =
                    lce.downlinkCapacityKbps - lce.secondaryDownlinkCapacityKbps;
        }
        if (primaryUplinkCapacityKbps != LinkCapacityEstimate.INVALID
                && lce.secondaryUplinkCapacityKbps != LinkCapacityEstimate.INVALID) {
            primaryUplinkCapacityKbps =
                    lce.uplinkCapacityKbps - lce.secondaryUplinkCapacityKbps;
        }
        lceList.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_PRIMARY,
                primaryDownlinkCapacityKbps, primaryUplinkCapacityKbps));
        lceList.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_SECONDARY,
                lce.secondaryDownlinkCapacityKbps, lce.secondaryUplinkCapacityKbps));
        return lceList;
    }

    /**
     * Convert a list of CellInfo defined in radio/1.0, 1.2, 1.4, 1.5, 1.6/types.hal to a list of
     * CellInfos
     * @param records List of CellInfo defined in radio/1.0, 1.2, 1.4, 1.5, 1.6/types.hal
     * @return The converted list of CellInfos
     */
    public static ArrayList<CellInfo> convertHalCellInfoList(ArrayList<Object> records) {
        ArrayList<CellInfo> response = new ArrayList<>(records.size());
        if (records.isEmpty()) return response;
        final long nanotime = SystemClock.elapsedRealtimeNanos();
        for (Object obj : records) {
            response.add(convertHalCellInfo(obj, nanotime));
        }
        return response;
    }

    private static CellInfo convertHalCellInfo(Object cellInfo, long nanotime) {
        if (cellInfo == null) return null;
        if (cellInfo instanceof android.hardware.radio.V1_0.CellInfo) {
            final android.hardware.radio.V1_0.CellInfo record =
                    (android.hardware.radio.V1_0.CellInfo) cellInfo;
            record.timeStamp = nanotime;
            return CellInfo.create(record);
        } else if (cellInfo instanceof android.hardware.radio.V1_2.CellInfo) {
            final android.hardware.radio.V1_2.CellInfo record =
                    (android.hardware.radio.V1_2.CellInfo) cellInfo;
            record.timeStamp = nanotime;
            return CellInfo.create(record);
        } else if (cellInfo instanceof android.hardware.radio.V1_4.CellInfo) {
            final android.hardware.radio.V1_4.CellInfo record =
                    (android.hardware.radio.V1_4.CellInfo) cellInfo;
            return CellInfo.create(record, nanotime);
        } else if (cellInfo instanceof android.hardware.radio.V1_5.CellInfo) {
            final android.hardware.radio.V1_5.CellInfo record =
                    (android.hardware.radio.V1_5.CellInfo) cellInfo;
            return CellInfo.create(record, nanotime);
        } else if (cellInfo instanceof android.hardware.radio.V1_6.CellInfo) {
            final android.hardware.radio.V1_6.CellInfo record =
                    (android.hardware.radio.V1_6.CellInfo) cellInfo;
            return CellInfo.create(record, nanotime);
        } else {
            return null;
        }
    }

    private static LinkAddress convertToLinkAddress(String addressString) {
        return convertToLinkAddress(addressString, 0, LinkAddress.LIFETIME_UNKNOWN,
                LinkAddress.LIFETIME_UNKNOWN);
    }

    private static LinkAddress convertToLinkAddress(String addressString, int properties,
            long deprecationTime, long expirationTime) {
        addressString = addressString.trim();
        InetAddress address = null;
        int prefixLength = -1;
        try {
            String[] pieces = addressString.split("/", 2);
            address = InetAddresses.parseNumericAddress(pieces[0]);
            if (pieces.length == 1) {
                prefixLength = (address instanceof Inet4Address) ? 32 : 128;
            } else if (pieces.length == 2) {
                prefixLength = Integer.parseInt(pieces[1]);
            }
        } catch (NullPointerException e) {            // Null string.
        } catch (ArrayIndexOutOfBoundsException e) {  // No prefix length.
        } catch (NumberFormatException e) {           // Non-numeric prefix.
        } catch (IllegalArgumentException e) {        // Invalid IP address.
        }

        if (address == null || prefixLength == -1) {
            throw new IllegalArgumentException("Invalid link address " + addressString);
        }

        return new LinkAddress(address, prefixLength, properties, 0, deprecationTime,
                expirationTime);
    }

    /**
     * Convert SetupDataCallResult defined in radio/1.0, 1.4, 1.5, 1.6/types.hal into
     * DataCallResponse
     * @param dcResult SetupDataCallResult defined in radio/1.0, 1.4, 1.5, 1.6/types.hal
     * @return The converted DataCallResponse
     */
    @VisibleForTesting
    public static DataCallResponse convertHalDataCallResult(Object dcResult) {
        if (dcResult == null) return null;

        int cause, cid, active, mtu, mtuV4, mtuV6;
        long suggestedRetryTime;
        String ifname;
        int protocolType;
        String[] addresses = null;
        String[] dnses = null;
        String[] gateways = null;
        String[] pcscfs = null;
        Qos defaultQos = null;
        @DataCallResponse.HandoverFailureMode
        int handoverFailureMode = DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY;
        int pduSessionId = DataCallResponse.PDU_SESSION_ID_NOT_SET;
        List<LinkAddress> laList = new ArrayList<>();
        List<QosBearerSession> qosSessions = new ArrayList<>();
        NetworkSliceInfo sliceInfo = null;
        List<TrafficDescriptor> trafficDescriptors = new ArrayList<>();

        if (dcResult instanceof android.hardware.radio.V1_0.SetupDataCallResult) {
            final android.hardware.radio.V1_0.SetupDataCallResult result =
                    (android.hardware.radio.V1_0.SetupDataCallResult) dcResult;
            cause = result.status;
            suggestedRetryTime = result.suggestedRetryTime;
            cid = result.cid;
            active = result.active;
            protocolType = ApnSetting.getProtocolIntFromString(result.type);
            ifname = result.ifname;
            if (!TextUtils.isEmpty(result.addresses)) {
                addresses = result.addresses.split("\\s+");
            }
            if (!TextUtils.isEmpty(result.dnses)) {
                dnses = result.dnses.split("\\s+");
            }
            if (!TextUtils.isEmpty(result.gateways)) {
                gateways = result.gateways.split("\\s+");
            }
            if (!TextUtils.isEmpty(result.pcscf)) {
                pcscfs = result.pcscf.split("\\s+");
            }
            mtu = mtuV4 = mtuV6 = result.mtu;
            if (addresses != null) {
                for (String address : addresses) {
                    laList.add(convertToLinkAddress(address));
                }
            }
        } else if (dcResult instanceof android.hardware.radio.V1_4.SetupDataCallResult) {
            final android.hardware.radio.V1_4.SetupDataCallResult result =
                    (android.hardware.radio.V1_4.SetupDataCallResult) dcResult;
            cause = result.cause;
            suggestedRetryTime = result.suggestedRetryTime;
            cid = result.cid;
            active = result.active;
            protocolType = result.type;
            ifname = result.ifname;
            addresses = result.addresses.toArray(new String[0]);
            dnses = result.dnses.toArray(new String[0]);
            gateways = result.gateways.toArray(new String[0]);
            pcscfs = result.pcscf.toArray(new String[0]);
            mtu = mtuV4 = mtuV6 = result.mtu;
            if (addresses != null) {
                for (String address : addresses) {
                    laList.add(convertToLinkAddress(address));
                }
            }
        } else if (dcResult instanceof android.hardware.radio.V1_5.SetupDataCallResult) {
            final android.hardware.radio.V1_5.SetupDataCallResult result =
                    (android.hardware.radio.V1_5.SetupDataCallResult) dcResult;
            cause = result.cause;
            suggestedRetryTime = result.suggestedRetryTime;
            cid = result.cid;
            active = result.active;
            protocolType = result.type;
            ifname = result.ifname;
            laList = result.addresses.stream().map(la -> convertToLinkAddress(
                    la.address, la.properties, la.deprecationTime, la.expirationTime))
                    .collect(Collectors.toList());
            dnses = result.dnses.toArray(new String[0]);
            gateways = result.gateways.toArray(new String[0]);
            pcscfs = result.pcscf.toArray(new String[0]);
            mtu = Math.max(result.mtuV4, result.mtuV6);
            mtuV4 = result.mtuV4;
            mtuV6 = result.mtuV6;
        } else if (dcResult instanceof android.hardware.radio.V1_6.SetupDataCallResult) {
            final android.hardware.radio.V1_6.SetupDataCallResult result =
                    (android.hardware.radio.V1_6.SetupDataCallResult) dcResult;
            cause = result.cause;
            suggestedRetryTime = result.suggestedRetryTime;
            cid = result.cid;
            active = result.active;
            protocolType = result.type;
            ifname = result.ifname;
            laList = result.addresses.stream().map(la -> convertToLinkAddress(
                    la.address, la.properties, la.deprecationTime, la.expirationTime))
                    .collect(Collectors.toList());
            dnses = result.dnses.toArray(new String[0]);
            gateways = result.gateways.toArray(new String[0]);
            pcscfs = result.pcscf.toArray(new String[0]);
            mtu = Math.max(result.mtuV4, result.mtuV6);
            mtuV4 = result.mtuV4;
            mtuV6 = result.mtuV6;
            handoverFailureMode = result.handoverFailureMode;
            pduSessionId = result.pduSessionId;
            defaultQos = convertHalQos(result.defaultQos);
            qosSessions = result.qosSessions.stream().map(RILUtils::convertHalQosBearerSession)
                    .collect(Collectors.toList());
            sliceInfo = result.sliceInfo.getDiscriminator()
                    == android.hardware.radio.V1_6.OptionalSliceInfo.hidl_discriminator.noinit
                    ? null : convertHalSliceInfo(result.sliceInfo.value());
            trafficDescriptors = result.trafficDescriptors.stream().map(
                    RILUtils::convertHalTrafficDescriptor).collect(Collectors.toList());
        } else {
            Rlog.e(LOG_TAG, "Unsupported SetupDataCallResult " + dcResult);
            return null;
        }

        // Process dns
        List<InetAddress> dnsList = new ArrayList<>();
        if (dnses != null) {
            for (String dns : dnses) {
                dns = dns.trim();
                InetAddress ia;
                try {
                    ia = InetAddresses.parseNumericAddress(dns);
                    dnsList.add(ia);
                } catch (IllegalArgumentException e) {
                    Rlog.e(LOG_TAG, "Unknown dns: " + dns, e);
                }
            }
        }

        // Process gateway
        List<InetAddress> gatewayList = new ArrayList<>();
        if (gateways != null) {
            for (String gateway : gateways) {
                gateway = gateway.trim();
                InetAddress ia;
                try {
                    ia = InetAddresses.parseNumericAddress(gateway);
                    gatewayList.add(ia);
                } catch (IllegalArgumentException e) {
                    Rlog.e(LOG_TAG, "Unknown gateway: " + gateway, e);
                }
            }
        }

        // Process gateway
        List<InetAddress> pcscfList = new ArrayList<>();
        if (pcscfs != null) {
            for (String pcscf : pcscfs) {
                pcscf = pcscf.trim();
                InetAddress ia;
                try {
                    ia = InetAddresses.parseNumericAddress(pcscf);
                    pcscfList.add(ia);
                } catch (IllegalArgumentException e) {
                    Rlog.e(LOG_TAG, "Unknown pcscf: " + pcscf, e);
                }
            }
        }

        return new DataCallResponse.Builder()
                .setCause(cause)
                .setRetryDurationMillis(suggestedRetryTime)
                .setId(cid)
                .setLinkStatus(active)
                .setProtocolType(protocolType)
                .setInterfaceName(ifname)
                .setAddresses(laList)
                .setDnsAddresses(dnsList)
                .setGatewayAddresses(gatewayList)
                .setPcscfAddresses(pcscfList)
                .setMtu(mtu)
                .setMtuV4(mtuV4)
                .setMtuV6(mtuV6)
                .setHandoverFailureMode(handoverFailureMode)
                .setPduSessionId(pduSessionId)
                .setDefaultQos(defaultQos)
                .setQosBearerSessions(qosSessions)
                .setSliceInfo(sliceInfo)
                .setTrafficDescriptors(trafficDescriptors)
                .build();
    }

    /**
     * Convert SetupDataCallResult defined in SetupDataCallResult.aidl into DataCallResponse
     * @param result SetupDataCallResult defined in SetupDataCallResult.aidl
     * @return The converted DataCallResponse
     */
    @VisibleForTesting
    public static DataCallResponse convertHalDataCallResult(
            android.hardware.radio.data.SetupDataCallResult result) {
        if (result == null) return null;
        List<LinkAddress> laList = new ArrayList<>();
        for (android.hardware.radio.data.LinkAddress la : result.addresses) {
            laList.add(convertToLinkAddress(la.address, la.addressProperties,
                    la.deprecationTime, la.expirationTime));
        }
        List<InetAddress> dnsList = new ArrayList<>();
        if (result.dnses != null) {
            for (String dns : result.dnses) {
                dns = dns.trim();
                InetAddress ia;
                try {
                    ia = InetAddresses.parseNumericAddress(dns);
                    dnsList.add(ia);
                } catch (IllegalArgumentException e) {
                    Rlog.e(LOG_TAG, "Unknown dns: " + dns, e);
                }
            }
        }
        List<InetAddress> gatewayList = new ArrayList<>();
        if (result.gateways != null) {
            for (String gateway : result.gateways) {
                gateway = gateway.trim();
                InetAddress ia;
                try {
                    ia = InetAddresses.parseNumericAddress(gateway);
                    gatewayList.add(ia);
                } catch (IllegalArgumentException e) {
                    Rlog.e(LOG_TAG, "Unknown gateway: " + gateway, e);
                }
            }
        }
        List<InetAddress> pcscfList = new ArrayList<>();
        if (result.pcscf != null) {
            for (String pcscf : result.pcscf) {
                pcscf = pcscf.trim();
                InetAddress ia;
                try {
                    ia = InetAddresses.parseNumericAddress(pcscf);
                    pcscfList.add(ia);
                } catch (IllegalArgumentException e) {
                    Rlog.e(LOG_TAG, "Unknown pcscf: " + pcscf, e);
                }
            }
        }
        List<QosBearerSession> qosSessions = new ArrayList<>();
        for (android.hardware.radio.data.QosSession session : result.qosSessions) {
            qosSessions.add(convertHalQosBearerSession(session));
        }
        List<TrafficDescriptor> trafficDescriptors = new ArrayList<>();
        for (android.hardware.radio.data.TrafficDescriptor td : result.trafficDescriptors) {
            trafficDescriptors.add(convertHalTrafficDescriptor(td));
        }

        return new DataCallResponse.Builder()
                .setCause(result.cause)
                .setRetryDurationMillis(result.suggestedRetryTime)
                .setId(result.cid)
                .setLinkStatus(result.active)
                .setProtocolType(result.type)
                .setInterfaceName(result.ifname)
                .setAddresses(laList)
                .setDnsAddresses(dnsList)
                .setGatewayAddresses(gatewayList)
                .setPcscfAddresses(pcscfList)
                .setMtu(Math.max(result.mtuV4, result.mtuV6))
                .setMtuV4(result.mtuV4)
                .setMtuV6(result.mtuV6)
                .setHandoverFailureMode(result.handoverFailureMode)
                .setPduSessionId(result.pduSessionId)
                .setDefaultQos(convertHalQos(result.defaultQos))
                .setQosBearerSessions(qosSessions)
                .setSliceInfo(convertHalSliceInfo(result.sliceInfo))
                .setTrafficDescriptors(trafficDescriptors)
                .build();
    }

    private static NetworkSliceInfo convertHalSliceInfo(android.hardware.radio.V1_6.SliceInfo si) {
        NetworkSliceInfo.Builder builder = new NetworkSliceInfo.Builder()
                .setSliceServiceType(si.sst)
                .setMappedHplmnSliceServiceType(si.mappedHplmnSst);
        if (si.sliceDifferentiator != NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE) {
            builder.setSliceDifferentiator(si.sliceDifferentiator)
                    .setMappedHplmnSliceDifferentiator(si.mappedHplmnSD);
        }
        return builder.build();
    }

    private static NetworkSliceInfo convertHalSliceInfo(android.hardware.radio.data.SliceInfo si) {
        NetworkSliceInfo.Builder builder = new NetworkSliceInfo.Builder()
                .setSliceServiceType(si.sliceServiceType)
                .setMappedHplmnSliceServiceType(si.mappedHplmnSst);
        if (si.sliceDifferentiator != NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE) {
            builder.setSliceDifferentiator(si.sliceDifferentiator)
                    .setMappedHplmnSliceDifferentiator(si.mappedHplmnSD);
        }
        return builder.build();
    }

    private static TrafficDescriptor convertHalTrafficDescriptor(
            android.hardware.radio.V1_6.TrafficDescriptor td) {
        String dnn = td.dnn.getDiscriminator()
                == android.hardware.radio.V1_6.OptionalDnn.hidl_discriminator.noinit
                ? null : td.dnn.value();
        String osAppId = td.osAppId.getDiscriminator()
                == android.hardware.radio.V1_6.OptionalOsAppId.hidl_discriminator.noinit
                ? null : new String(arrayListToPrimitiveArray(td.osAppId.value().osAppId));
        TrafficDescriptor.Builder builder = new TrafficDescriptor.Builder();
        if (dnn != null) {
            builder.setDataNetworkName(dnn);
        }
        if (osAppId != null) {
            builder.setOsAppId(osAppId.getBytes());
        }
        return builder.build();
    }

    private static TrafficDescriptor convertHalTrafficDescriptor(
            android.hardware.radio.data.TrafficDescriptor td) {
        String dnn = td.dnn;
        String osAppId = td.osAppId == null ? null : new String(td.osAppId.osAppId);
        TrafficDescriptor.Builder builder = new TrafficDescriptor.Builder();
        if (dnn != null) {
            builder.setDataNetworkName(dnn);
        }
        if (osAppId != null) {
            builder.setOsAppId(osAppId.getBytes());
        }
        return builder.build();
    }

    /**
     * Convert SlicingConfig defined in radio/1.6/types.hal to NetworkSlicingConfig
     * @param sc SlicingConfig defined in radio/1.6/types.hal
     * @return The converted NetworkSlicingConfig
     */
    public static NetworkSlicingConfig convertHalSlicingConfig(
            android.hardware.radio.V1_6.SlicingConfig sc) {
        List<UrspRule> urspRules = sc.urspRules.stream().map(ur -> new UrspRule(ur.precedence,
                ur.trafficDescriptors.stream().map(RILUtils::convertHalTrafficDescriptor)
                        .collect(Collectors.toList()),
                ur.routeSelectionDescriptor.stream().map(rsd -> new RouteSelectionDescriptor(
                        rsd.precedence, rsd.sessionType.value(), rsd.sscMode.value(),
                        rsd.sliceInfo.stream().map(RILUtils::convertHalSliceInfo)
                                .collect(Collectors.toList()),
                        rsd.dnn)).collect(Collectors.toList())))
                .collect(Collectors.toList());
        return new NetworkSlicingConfig(urspRules, sc.sliceInfo.stream()
                .map(RILUtils::convertHalSliceInfo).collect(Collectors.toList()));
    }

    /**
     * Convert SlicingConfig defined in SlicingConfig.aidl to NetworkSlicingConfig
     * @param sc SlicingConfig defined in SlicingConfig.aidl
     * @return The converted NetworkSlicingConfig
     */
    public static NetworkSlicingConfig convertHalSlicingConfig(
            android.hardware.radio.data.SlicingConfig sc) {
        List<UrspRule> urspRules = new ArrayList<>();
        for (android.hardware.radio.data.UrspRule ur : sc.urspRules) {
            List<TrafficDescriptor> tds = new ArrayList<>();
            for (android.hardware.radio.data.TrafficDescriptor td : ur.trafficDescriptors) {
                tds.add(convertHalTrafficDescriptor(td));
            }
            List<RouteSelectionDescriptor> rsds = new ArrayList<>();
            for (android.hardware.radio.data.RouteSelectionDescriptor rsd
                    : ur.routeSelectionDescriptor) {
                List<NetworkSliceInfo> sliceInfo = new ArrayList<>();
                for (android.hardware.radio.data.SliceInfo si : rsd.sliceInfo) {
                    sliceInfo.add(convertHalSliceInfo(si));
                }
                rsds.add(new RouteSelectionDescriptor(rsd.precedence, rsd.sessionType, rsd.sscMode,
                        sliceInfo, primitiveArrayToArrayList(rsd.dnn)));
            }
            urspRules.add(new UrspRule(ur.precedence, tds, rsds));
        }
        List<NetworkSliceInfo> sliceInfo = new ArrayList<>();
        for (android.hardware.radio.data.SliceInfo si : sc.sliceInfo) {
            sliceInfo.add(convertHalSliceInfo(si));
        }
        return new NetworkSlicingConfig(urspRules, sliceInfo);
    }

    private static Qos.QosBandwidth convertHalQosBandwidth(
            android.hardware.radio.V1_6.QosBandwidth bandwidth) {
        return new Qos.QosBandwidth(bandwidth.maxBitrateKbps, bandwidth.guaranteedBitrateKbps);
    }

    private static Qos.QosBandwidth convertHalQosBandwidth(
            android.hardware.radio.data.QosBandwidth bandwidth) {
        return new Qos.QosBandwidth(bandwidth.maxBitrateKbps, bandwidth.guaranteedBitrateKbps);
    }

    private static Qos convertHalQos(android.hardware.radio.V1_6.Qos qos) {
        switch (qos.getDiscriminator()) {
            case android.hardware.radio.V1_6.Qos.hidl_discriminator.eps:
                android.hardware.radio.V1_6.EpsQos eps = qos.eps();
                return new EpsQos(convertHalQosBandwidth(eps.downlink),
                        convertHalQosBandwidth(eps.uplink), eps.qci);
            case android.hardware.radio.V1_6.Qos.hidl_discriminator.nr:
                android.hardware.radio.V1_6.NrQos nr = qos.nr();
                return new NrQos(convertHalQosBandwidth(nr.downlink),
                        convertHalQosBandwidth(nr.uplink), nr.qfi, nr.fiveQi, nr.averagingWindowMs);
            default:
                return null;
        }
    }

    private static Qos convertHalQos(android.hardware.radio.data.Qos qos) {
        switch (qos.getTag()) {
            case android.hardware.radio.data.Qos.eps:
                android.hardware.radio.data.EpsQos eps = qos.getEps();
                return new EpsQos(convertHalQosBandwidth(eps.downlink),
                        convertHalQosBandwidth(eps.uplink), eps.qci);
            case android.hardware.radio.data.Qos.nr:
                android.hardware.radio.data.NrQos nr = qos.getNr();
                return new NrQos(convertHalQosBandwidth(nr.downlink),
                        convertHalQosBandwidth(nr.uplink), nr.qfi, nr.fiveQi,
                        nr.averagingWindowMs);
            default:
                return null;
        }
    }

    private static QosBearerFilter convertHalQosBearerFilter(
            android.hardware.radio.V1_6.QosFilter qosFilter) {
        List<LinkAddress> localAddressList = new ArrayList<>();
        String[] localAddresses = qosFilter.localAddresses.toArray(new String[0]);
        if (localAddresses != null) {
            for (String address : localAddresses) {
                localAddressList.add(convertToLinkAddress(address));
            }
        }
        List<LinkAddress> remoteAddressList = new ArrayList<>();
        String[] remoteAddresses = qosFilter.remoteAddresses.toArray(new String[0]);
        if (remoteAddresses != null) {
            for (String address : remoteAddresses) {
                remoteAddressList.add(convertToLinkAddress(address));
            }
        }
        QosBearerFilter.PortRange localPort = null;
        if (qosFilter.localPort != null) {
            if (qosFilter.localPort.getDiscriminator()
                    == android.hardware.radio.V1_6.MaybePort.hidl_discriminator.range) {
                final android.hardware.radio.V1_6.PortRange portRange = qosFilter.localPort.range();
                localPort = new QosBearerFilter.PortRange(portRange.start, portRange.end);
            }
        }
        QosBearerFilter.PortRange remotePort = null;
        if (qosFilter.remotePort != null) {
            if (qosFilter.remotePort.getDiscriminator()
                    == android.hardware.radio.V1_6.MaybePort.hidl_discriminator.range) {
                final android.hardware.radio.V1_6.PortRange portRange =
                        qosFilter.remotePort.range();
                remotePort = new QosBearerFilter.PortRange(portRange.start, portRange.end);
            }
        }
        int tos = -1;
        if (qosFilter.tos != null) {
            if (qosFilter.tos.getDiscriminator() == android.hardware.radio.V1_6.QosFilter
                    .TypeOfService.hidl_discriminator.value) {
                tos = qosFilter.tos.value();
            }
        }
        long flowLabel = -1;
        if (qosFilter.flowLabel != null) {
            if (qosFilter.flowLabel.getDiscriminator() == android.hardware.radio.V1_6.QosFilter
                    .Ipv6FlowLabel.hidl_discriminator.value) {
                flowLabel = qosFilter.flowLabel.value();
            }
        }
        long spi = -1;
        if (qosFilter.spi != null) {
            if (qosFilter.spi.getDiscriminator()
                    == android.hardware.radio.V1_6.QosFilter.IpsecSpi.hidl_discriminator.value) {
                spi = qosFilter.spi.value();
            }
        }
        return new QosBearerFilter(localAddressList, remoteAddressList, localPort, remotePort,
                qosFilter.protocol, tos, flowLabel, spi, qosFilter.direction, qosFilter.precedence);
    }

    private static QosBearerFilter convertHalQosBearerFilter(
            android.hardware.radio.data.QosFilter qosFilter) {
        List<LinkAddress> localAddressList = new ArrayList<>();
        String[] localAddresses = qosFilter.localAddresses;
        if (localAddresses != null) {
            for (String address : localAddresses) {
                localAddressList.add(convertToLinkAddress(address));
            }
        }
        List<LinkAddress> remoteAddressList = new ArrayList<>();
        String[] remoteAddresses = qosFilter.remoteAddresses;
        if (remoteAddresses != null) {
            for (String address : remoteAddresses) {
                remoteAddressList.add(convertToLinkAddress(address));
            }
        }
        QosBearerFilter.PortRange localPort = null;
        if (qosFilter.localPort != null) {
            localPort = new QosBearerFilter.PortRange(
                    qosFilter.localPort.start, qosFilter.localPort.end);
        }
        QosBearerFilter.PortRange remotePort = null;
        if (qosFilter.remotePort != null) {
            remotePort = new QosBearerFilter.PortRange(
                    qosFilter.remotePort.start, qosFilter.remotePort.end);
        }
        int tos = -1;
        if (qosFilter.tos != null) {
            if (qosFilter.tos.getTag()
                    == android.hardware.radio.data.QosFilterTypeOfService.value) {
                tos = qosFilter.tos.value;
            }
        }
        long flowLabel = -1;
        if (qosFilter.flowLabel != null) {
            if (qosFilter.flowLabel.getTag()
                    == android.hardware.radio.data.QosFilterIpv6FlowLabel.value) {
                flowLabel = qosFilter.flowLabel.value;
            }
        }
        long spi = -1;
        if (qosFilter.spi != null) {
            if (qosFilter.spi.getTag()
                    == android.hardware.radio.data.QosFilterIpsecSpi.value) {
                spi = qosFilter.spi.value;
            }
        }
        return new QosBearerFilter(localAddressList, remoteAddressList, localPort, remotePort,
                qosFilter.protocol, tos, flowLabel, spi, qosFilter.direction, qosFilter.precedence);
    }

    private static QosBearerSession convertHalQosBearerSession(
            android.hardware.radio.V1_6.QosSession qosSession) {
        List<QosBearerFilter> qosBearerFilters = new ArrayList<>();
        if (qosSession.qosFilters != null) {
            for (android.hardware.radio.V1_6.QosFilter filter : qosSession.qosFilters) {
                qosBearerFilters.add(convertHalQosBearerFilter(filter));
            }
        }
        return new QosBearerSession(qosSession.qosSessionId, convertHalQos(qosSession.qos),
                qosBearerFilters);
    }

    private static QosBearerSession convertHalQosBearerSession(
            android.hardware.radio.data.QosSession qosSession) {
        List<QosBearerFilter> qosBearerFilters = new ArrayList<>();
        if (qosSession.qosFilters != null) {
            for (android.hardware.radio.data.QosFilter filter : qosSession.qosFilters) {
                qosBearerFilters.add(convertHalQosBearerFilter(filter));
            }
        }
        return new QosBearerSession(qosSession.qosSessionId, convertHalQos(qosSession.qos),
                qosBearerFilters);
    }

    /**
     * Convert a list of SetupDataCallResult defined in radio/1.0, 1.4, 1.5, 1.6/types.hal into
     * a list of DataCallResponse
     * @param dataCallResultList List of SetupDataCallResult defined in
     *        radio/1.0, 1.4, 1.5, 1.6/types.hal
     * @return The converted list of DataCallResponses
     */
    @VisibleForTesting
    public static ArrayList<DataCallResponse> convertHalDataCallResultList(
            List<? extends Object> dataCallResultList) {
        ArrayList<DataCallResponse> response = new ArrayList<>(dataCallResultList.size());

        for (Object obj : dataCallResultList) {
            response.add(convertHalDataCallResult(obj));
        }
        return response;
    }

    /**
     * Convert a list of SetupDataCallResult defined in SetupDataCallResult.aidl into a list of
     * DataCallResponse
     * @param dataCallResultList Array of SetupDataCallResult defined in SetupDataCallResult.aidl
     * @return The converted list of DataCallResponses
     */
    @VisibleForTesting
    public static ArrayList<DataCallResponse> convertHalDataCallResultList(
            android.hardware.radio.data.SetupDataCallResult[] dataCallResultList) {
        ArrayList<DataCallResponse> response = new ArrayList<>(dataCallResultList.length);

        for (android.hardware.radio.data.SetupDataCallResult result : dataCallResultList) {
            response.add(convertHalDataCallResult(result));
        }
        return response;
    }

    /**
     * Convert KeepaliveStatusCode defined in radio/1.1/types.hal and KeepaliveStatus.aidl
     * to KeepaliveStatus
     * @param halCode KeepaliveStatus code defined in radio/1.1/types.hal or KeepaliveStatus.aidl
     * @return The converted KeepaliveStatus
     */
    public static int convertHalKeepaliveStatusCode(int halCode) {
        switch (halCode) {
            case android.hardware.radio.V1_1.KeepaliveStatusCode.ACTIVE:
                return KeepaliveStatus.STATUS_ACTIVE;
            case android.hardware.radio.V1_1.KeepaliveStatusCode.INACTIVE:
                return KeepaliveStatus.STATUS_INACTIVE;
            case android.hardware.radio.V1_1.KeepaliveStatusCode.PENDING:
                return KeepaliveStatus.STATUS_PENDING;
            default:
                return -1;
        }
    }

    /**
     * Convert RadioState defined in radio/1.0/types.hal to RadioPowerState
     * @param stateInt Radio state defined in radio/1.0/types.hal
     * @return The converted {@link Annotation.RadioPowerState RadioPowerState}
     */
    public static @Annotation.RadioPowerState int convertHalRadioState(int stateInt) {
        int state;
        switch(stateInt) {
            case android.hardware.radio.V1_0.RadioState.OFF:
                state = TelephonyManager.RADIO_POWER_OFF;
                break;
            case android.hardware.radio.V1_0.RadioState.UNAVAILABLE:
                state = TelephonyManager.RADIO_POWER_UNAVAILABLE;
                break;
            case android.hardware.radio.V1_0.RadioState.ON:
                state = TelephonyManager.RADIO_POWER_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RadioState: " + stateInt);
        }
        return state;
    }

    /**
     * Convert CellConnectionStatus defined in radio/1.2/types.hal to ConnectionStatus
     * @param status Cell connection status defined in radio/1.2/types.hal
     * @return The converted ConnectionStatus
     */
    public static int convertHalCellConnectionStatus(int status) {
        switch (status) {
            case android.hardware.radio.V1_2.CellConnectionStatus.PRIMARY_SERVING:
                return PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING;
            case android.hardware.radio.V1_2.CellConnectionStatus.SECONDARY_SERVING:
                return PhysicalChannelConfig.CONNECTION_SECONDARY_SERVING;
            default:
                return PhysicalChannelConfig.CONNECTION_UNKNOWN;
        }
    }

    /**
     * Convert Call defined in radio/1.0, 1.2, 1.6/types.hal to DriverCall
     * @param halCall Call defined in radio/1.0, 1.2, 1.6/types.hal
     * @return The converted DriverCall
     */
    public static DriverCall convertToDriverCall(Object halCall) {
        DriverCall dc = new DriverCall();
        final android.hardware.radio.V1_6.Call call16;
        final android.hardware.radio.V1_2.Call call12;
        final android.hardware.radio.V1_0.Call call10;
        if (halCall instanceof android.hardware.radio.V1_6.Call) {
            call16 = (android.hardware.radio.V1_6.Call) halCall;
            call12 = call16.base;
            call10 = call12.base;
        } else if (halCall instanceof android.hardware.radio.V1_2.Call) {
            call16 = null;
            call12 = (android.hardware.radio.V1_2.Call) halCall;
            call10 = call12.base;
        } else if (halCall instanceof android.hardware.radio.V1_0.Call) {
            call16 = null;
            call12 = null;
            call10 = (android.hardware.radio.V1_0.Call) halCall;
        } else {
            call16 = null;
            call12 = null;
            call10 = null;
        }
        if (call10 != null) {
            dc.state = DriverCall.stateFromCLCC((int) (call10.state));
            dc.index = call10.index;
            dc.TOA = call10.toa;
            dc.isMpty = call10.isMpty;
            dc.isMT = call10.isMT;
            dc.als = call10.als;
            dc.isVoice = call10.isVoice;
            dc.isVoicePrivacy = call10.isVoicePrivacy;
            dc.number = call10.number;
            dc.numberPresentation = DriverCall.presentationFromCLIP(
                    (int) (call10.numberPresentation));
            dc.name = call10.name;
            dc.namePresentation = DriverCall.presentationFromCLIP((int) (call10.namePresentation));
            if (call10.uusInfo.size() == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(call10.uusInfo.get(0).uusType);
                dc.uusInfo.setDcs(call10.uusInfo.get(0).uusDcs);
                if (!TextUtils.isEmpty(call10.uusInfo.get(0).uusData)) {
                    byte[] userData = call10.uusInfo.get(0).uusData.getBytes();
                    dc.uusInfo.setUserData(userData);
                }
            }
            // Make sure there's a leading + on addresses with a TOA of 145
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);
        }
        if (call12 != null) {
            dc.audioQuality = (int) (call12.audioQuality);
        }
        if (call16 != null) {
            dc.forwardedNumber = call16.forwardedNumber;
        }
        return dc;
    }

    /**
     * Convert OperatorStatus defined in radio/1.0/types.hal to OperatorInfo.State
     * @param status Operator status defined in radio/1.0/types.hal
     * @return The converted OperatorStatus as a String
     */
    public static String convertHalOperatorStatus(int status) {
        if (status == android.hardware.radio.V1_0.OperatorStatus.UNKNOWN) {
            return "unknown";
        } else if (status == android.hardware.radio.V1_0.OperatorStatus.AVAILABLE) {
            return "available";
        } else if (status == android.hardware.radio.V1_0.OperatorStatus.CURRENT) {
            return "current";
        } else if (status == android.hardware.radio.V1_0.OperatorStatus.FORBIDDEN) {
            return "forbidden";
        } else {
            return "";
        }
    }

    /**
     * Convert a list of Carriers defined in radio/1.0/types.hal to a list of CarrierIdentifiers
     * @param carrierList List of Carriers defined in radio/1.0/types.hal
     * @return The converted list of CarrierIdentifiers
     */
    public static List<CarrierIdentifier> convertHalCarrierList(
            List<android.hardware.radio.V1_0.Carrier> carrierList) {
        List<CarrierIdentifier> ret = new ArrayList<>();
        for (int i = 0; i < carrierList.size(); i++) {
            String mcc = carrierList.get(i).mcc;
            String mnc = carrierList.get(i).mnc;
            String spn = null, imsi = null, gid1 = null, gid2 = null;
            int matchType = carrierList.get(i).matchType;
            String matchData = carrierList.get(i).matchData;
            if (matchType == CarrierIdentifier.MatchType.SPN) {
                spn = matchData;
            } else if (matchType == CarrierIdentifier.MatchType.IMSI_PREFIX) {
                imsi = matchData;
            } else if (matchType == CarrierIdentifier.MatchType.GID1) {
                gid1 = matchData;
            } else if (matchType == CarrierIdentifier.MatchType.GID2) {
                gid2 = matchData;
            }
            ret.add(new CarrierIdentifier(mcc, mnc, spn, imsi, gid1, gid2));
        }
        return ret;
    }

    /**
     * Convert CardStatus defined in radio/1.0, 1.5/types.hal to IccCardStatus
     * @param cardStatus CardStatus defined in radio/1.0, 1.5/types.hal
     * @return The converted IccCardStatus
     */
    public static IccCardStatus convertHalCardStatus(Object cardStatus) {
        final android.hardware.radio.V1_0.CardStatus cardStatus10;
        final android.hardware.radio.V1_5.CardStatus cardStatus15;
        if (cardStatus instanceof android.hardware.radio.V1_5.CardStatus) {
            cardStatus15 = (android.hardware.radio.V1_5.CardStatus) cardStatus;
            cardStatus10 = cardStatus15.base.base.base;
        } else if (cardStatus instanceof android.hardware.radio.V1_0.CardStatus) {
            cardStatus15 = null;
            cardStatus10 = (android.hardware.radio.V1_0.CardStatus) cardStatus;
        } else {
            cardStatus15 = null;
            cardStatus10 = null;
        }

        IccCardStatus iccCardStatus = new IccCardStatus();
        if (cardStatus10 != null) {
            iccCardStatus.setCardState(cardStatus10.cardState);
            iccCardStatus.setUniversalPinState(cardStatus10.universalPinState);
            iccCardStatus.mGsmUmtsSubscriptionAppIndex = cardStatus10.gsmUmtsSubscriptionAppIndex;
            iccCardStatus.mCdmaSubscriptionAppIndex = cardStatus10.cdmaSubscriptionAppIndex;
            iccCardStatus.mImsSubscriptionAppIndex = cardStatus10.imsSubscriptionAppIndex;
            int numApplications = cardStatus10.applications.size();

            // limit to maximum allowed applications
            if (numApplications > com.android.internal.telephony.uicc.IccCardStatus.CARD_MAX_APPS) {
                numApplications = com.android.internal.telephony.uicc.IccCardStatus.CARD_MAX_APPS;
            }
            iccCardStatus.mApplications = new IccCardApplicationStatus[numApplications];
            for (int i = 0; i < numApplications; i++) {
                android.hardware.radio.V1_0.AppStatus rilAppStatus =
                        cardStatus10.applications.get(i);
                IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
                appStatus.app_type = appStatus.AppTypeFromRILInt(rilAppStatus.appType);
                appStatus.app_state = appStatus.AppStateFromRILInt(rilAppStatus.appState);
                appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(
                        rilAppStatus.persoSubstate);
                appStatus.aid = rilAppStatus.aidPtr;
                appStatus.app_label = rilAppStatus.appLabelPtr;
                appStatus.pin1_replaced = rilAppStatus.pin1Replaced;
                appStatus.pin1 = appStatus.PinStateFromRILInt(rilAppStatus.pin1);
                appStatus.pin2 = appStatus.PinStateFromRILInt(rilAppStatus.pin2);
                iccCardStatus.mApplications[i] = appStatus;
            }
        }
        if (cardStatus15 != null) {
            iccCardStatus.physicalSlotIndex = cardStatus15.base.base.physicalSlotId;
            iccCardStatus.atr = cardStatus15.base.base.atr;
            iccCardStatus.iccid = cardStatus15.base.base.iccid;
            iccCardStatus.eid = cardStatus15.base.eid;
            int numApplications = cardStatus15.applications.size();

            // limit to maximum allowed applications
            if (numApplications > com.android.internal.telephony.uicc.IccCardStatus.CARD_MAX_APPS) {
                numApplications = com.android.internal.telephony.uicc.IccCardStatus.CARD_MAX_APPS;
            }
            iccCardStatus.mApplications = new IccCardApplicationStatus[numApplications];
            for (int i = 0; i < numApplications; i++) {
                android.hardware.radio.V1_5.AppStatus rilAppStatus =
                        cardStatus15.applications.get(i);
                IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
                appStatus.app_type = appStatus.AppTypeFromRILInt(rilAppStatus.base.appType);
                appStatus.app_state = appStatus.AppStateFromRILInt(rilAppStatus.base.appState);
                appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(
                        rilAppStatus.persoSubstate);
                appStatus.aid = rilAppStatus.base.aidPtr;
                appStatus.app_label = rilAppStatus.base.appLabelPtr;
                appStatus.pin1_replaced = rilAppStatus.base.pin1Replaced;
                appStatus.pin1 = appStatus.PinStateFromRILInt(rilAppStatus.base.pin1);
                appStatus.pin2 = appStatus.PinStateFromRILInt(rilAppStatus.base.pin2);
                iccCardStatus.mApplications[i] = appStatus;
            }
        }
        return iccCardStatus;
    }

    /** Append the data to the end of an ArrayList */
    public static void appendPrimitiveArrayToArrayList(byte[] src, ArrayList<Byte> dst) {
        for (byte b : src) {
            dst.add(b);
        }
    }

    /** Convert a primitive byte array to an ArrayList<Integer>. */
    public static ArrayList<Byte> primitiveArrayToArrayList(byte[] arr) {
        ArrayList<Byte> arrayList = new ArrayList<>(arr.length);
        for (byte b : arr) {
            arrayList.add(b);
        }
        return arrayList;
    }

    /** Convert a primitive int array to an ArrayList<Integer>. */
    public static ArrayList<Integer> primitiveArrayToArrayList(int[] arr) {
        ArrayList<Integer> arrayList = new ArrayList<>(arr.length);
        for (int i : arr) {
            arrayList.add(i);
        }
        return arrayList;
    }

    /** Convert a primitive String array to an ArrayList<String>. */
    public static ArrayList<String> primitiveArrayToArrayList(String[] arr) {
        return new ArrayList<>(Arrays.asList(arr));
    }

    /** Convert an ArrayList of Bytes to an exactly-sized primitive array */
    public static byte[] arrayListToPrimitiveArray(ArrayList<Byte> bytes) {
        byte[] ret = new byte[bytes.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = bytes.get(i);
        }
        return ret;
    }

    /** Convert null to an empty String */
    public static String convertNullToEmptyString(String string) {
        return string != null ? string : "";
    }

    /**
     * RIL request to String
     * @param request request
     * @return The converted String request
     */
    public static String requestToString(int request) {
        switch(request) {
            case RIL_REQUEST_GET_SIM_STATUS:
                return "GET_SIM_STATUS";
            case RIL_REQUEST_ENTER_SIM_PIN:
                return "ENTER_SIM_PIN";
            case RIL_REQUEST_ENTER_SIM_PUK:
                return "ENTER_SIM_PUK";
            case RIL_REQUEST_ENTER_SIM_PIN2:
                return "ENTER_SIM_PIN2";
            case RIL_REQUEST_ENTER_SIM_PUK2:
                return "ENTER_SIM_PUK2";
            case RIL_REQUEST_CHANGE_SIM_PIN:
                return "CHANGE_SIM_PIN";
            case RIL_REQUEST_CHANGE_SIM_PIN2:
                return "CHANGE_SIM_PIN2";
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION:
                return "ENTER_NETWORK_DEPERSONALIZATION";
            case RIL_REQUEST_GET_CURRENT_CALLS:
                return "GET_CURRENT_CALLS";
            case RIL_REQUEST_DIAL:
                return "DIAL";
            case RIL_REQUEST_GET_IMSI:
                return "GET_IMSI";
            case RIL_REQUEST_HANGUP:
                return "HANGUP";
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND:
                return "HANGUP_WAITING_OR_BACKGROUND";
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND:
                return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE:
                return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case RIL_REQUEST_CONFERENCE:
                return "CONFERENCE";
            case RIL_REQUEST_UDUB:
                return "UDUB";
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE:
                return "LAST_CALL_FAIL_CAUSE";
            case RIL_REQUEST_SIGNAL_STRENGTH:
                return "SIGNAL_STRENGTH";
            case RIL_REQUEST_VOICE_REGISTRATION_STATE:
                return "VOICE_REGISTRATION_STATE";
            case RIL_REQUEST_DATA_REGISTRATION_STATE:
                return "DATA_REGISTRATION_STATE";
            case RIL_REQUEST_OPERATOR:
                return "OPERATOR";
            case RIL_REQUEST_RADIO_POWER:
                return "RADIO_POWER";
            case RIL_REQUEST_DTMF:
                return "DTMF";
            case RIL_REQUEST_SEND_SMS:
                return "SEND_SMS";
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE:
                return "SEND_SMS_EXPECT_MORE";
            case RIL_REQUEST_SETUP_DATA_CALL:
                return "SETUP_DATA_CALL";
            case RIL_REQUEST_SIM_IO:
                return "SIM_IO";
            case RIL_REQUEST_SEND_USSD:
                return "SEND_USSD";
            case RIL_REQUEST_CANCEL_USSD:
                return "CANCEL_USSD";
            case RIL_REQUEST_GET_CLIR:
                return "GET_CLIR";
            case RIL_REQUEST_SET_CLIR:
                return "SET_CLIR";
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS:
                return "QUERY_CALL_FORWARD_STATUS";
            case RIL_REQUEST_SET_CALL_FORWARD:
                return "SET_CALL_FORWARD";
            case RIL_REQUEST_QUERY_CALL_WAITING:
                return "QUERY_CALL_WAITING";
            case RIL_REQUEST_SET_CALL_WAITING:
                return "SET_CALL_WAITING";
            case RIL_REQUEST_SMS_ACKNOWLEDGE:
                return "SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GET_IMEI:
                return "GET_IMEI";
            case RIL_REQUEST_GET_IMEISV:
                return "GET_IMEISV";
            case RIL_REQUEST_ANSWER:
                return "ANSWER";
            case RIL_REQUEST_DEACTIVATE_DATA_CALL:
                return "DEACTIVATE_DATA_CALL";
            case RIL_REQUEST_QUERY_FACILITY_LOCK:
                return "QUERY_FACILITY_LOCK";
            case RIL_REQUEST_SET_FACILITY_LOCK:
                return "SET_FACILITY_LOCK";
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD:
                return "CHANGE_BARRING_PASSWORD";
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE:
                return "QUERY_NETWORK_SELECTION_MODE";
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC:
                return "SET_NETWORK_SELECTION_AUTOMATIC";
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL:
                return "SET_NETWORK_SELECTION_MANUAL";
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS :
                return "QUERY_AVAILABLE_NETWORKS ";
            case RIL_REQUEST_DTMF_START:
                return "DTMF_START";
            case RIL_REQUEST_DTMF_STOP:
                return "DTMF_STOP";
            case RIL_REQUEST_BASEBAND_VERSION:
                return "BASEBAND_VERSION";
            case RIL_REQUEST_SEPARATE_CONNECTION:
                return "SEPARATE_CONNECTION";
            case RIL_REQUEST_SET_MUTE:
                return "SET_MUTE";
            case RIL_REQUEST_GET_MUTE:
                return "GET_MUTE";
            case RIL_REQUEST_QUERY_CLIP:
                return "QUERY_CLIP";
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE:
                return "LAST_DATA_CALL_FAIL_CAUSE";
            case RIL_REQUEST_DATA_CALL_LIST:
                return "DATA_CALL_LIST";
            case RIL_REQUEST_RESET_RADIO:
                return "RESET_RADIO";
            case RIL_REQUEST_OEM_HOOK_RAW:
                return "OEM_HOOK_RAW";
            case RIL_REQUEST_OEM_HOOK_STRINGS:
                return "OEM_HOOK_STRINGS";
            case RIL_REQUEST_SCREEN_STATE:
                return "SCREEN_STATE";
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION:
                return "SET_SUPP_SVC_NOTIFICATION";
            case RIL_REQUEST_WRITE_SMS_TO_SIM:
                return "WRITE_SMS_TO_SIM";
            case RIL_REQUEST_DELETE_SMS_ON_SIM:
                return "DELETE_SMS_ON_SIM";
            case RIL_REQUEST_SET_BAND_MODE:
                return "SET_BAND_MODE";
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE:
                return "QUERY_AVAILABLE_BAND_MODE";
            case RIL_REQUEST_STK_GET_PROFILE:
                return "STK_GET_PROFILE";
            case RIL_REQUEST_STK_SET_PROFILE:
                return "STK_SET_PROFILE";
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND:
                return "STK_SEND_ENVELOPE_COMMAND";
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE:
                return "STK_SEND_TERMINAL_RESPONSE";
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM:
                return "STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER:
                return "EXPLICIT_CALL_TRANSFER";
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE:
                return "SET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE:
                return "GET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS:
                return "GET_NEIGHBORING_CELL_IDS";
            case RIL_REQUEST_SET_LOCATION_UPDATES:
                return "SET_LOCATION_UPDATES";
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE:
                return "CDMA_SET_SUBSCRIPTION_SOURCE";
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE:
                return "CDMA_SET_ROAMING_PREFERENCE";
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE:
                return "CDMA_QUERY_ROAMING_PREFERENCE";
            case RIL_REQUEST_SET_TTY_MODE:
                return "SET_TTY_MODE";
            case RIL_REQUEST_QUERY_TTY_MODE:
                return "QUERY_TTY_MODE";
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE:
                return "CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE:
                return "CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_FLASH:
                return "CDMA_FLASH";
            case RIL_REQUEST_CDMA_BURST_DTMF:
                return "CDMA_BURST_DTMF";
            case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY:
                return "CDMA_VALIDATE_AND_WRITE_AKEY";
            case RIL_REQUEST_CDMA_SEND_SMS:
                return "CDMA_SEND_SMS";
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE:
                return "CDMA_SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG:
                return "GSM_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG:
                return "GSM_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_GSM_BROADCAST_ACTIVATION:
                return "GSM_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG:
                return "CDMA_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG:
                return "CDMA_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION:
                return "CDMA_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_SUBSCRIPTION:
                return "CDMA_SUBSCRIPTION";
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM:
                return "CDMA_WRITE_SMS_TO_RUIM";
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM:
                return "CDMA_DELETE_SMS_ON_RUIM";
            case RIL_REQUEST_DEVICE_IDENTITY:
                return "DEVICE_IDENTITY";
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE:
                return "EXIT_EMERGENCY_CALLBACK_MODE";
            case RIL_REQUEST_GET_SMSC_ADDRESS:
                return "GET_SMSC_ADDRESS";
            case RIL_REQUEST_SET_SMSC_ADDRESS:
                return "SET_SMSC_ADDRESS";
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS:
                return "REPORT_SMS_MEMORY_STATUS";
            case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING:
                return "REPORT_STK_SERVICE_IS_RUNNING";
            case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE:
                return "CDMA_GET_SUBSCRIPTION_SOURCE";
            case RIL_REQUEST_ISIM_AUTHENTICATION:
                return "ISIM_AUTHENTICATION";
            case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU:
                return "ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS:
                return "STK_SEND_ENVELOPE_WITH_STATUS";
            case RIL_REQUEST_VOICE_RADIO_TECH:
                return "VOICE_RADIO_TECH";
            case RIL_REQUEST_GET_CELL_INFO_LIST:
                return "GET_CELL_INFO_LIST";
            case RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE:
                return "SET_CELL_INFO_LIST_RATE";
            case RIL_REQUEST_SET_INITIAL_ATTACH_APN:
                return "SET_INITIAL_ATTACH_APN";
            case RIL_REQUEST_IMS_REGISTRATION_STATE:
                return "IMS_REGISTRATION_STATE";
            case RIL_REQUEST_IMS_SEND_SMS:
                return "IMS_SEND_SMS";
            case RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC:
                return "SIM_TRANSMIT_APDU_BASIC";
            case RIL_REQUEST_SIM_OPEN_CHANNEL:
                return "SIM_OPEN_CHANNEL";
            case RIL_REQUEST_SIM_CLOSE_CHANNEL:
                return "SIM_CLOSE_CHANNEL";
            case RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL:
                return "SIM_TRANSMIT_APDU_CHANNEL";
            case RIL_REQUEST_NV_READ_ITEM:
                return "NV_READ_ITEM";
            case RIL_REQUEST_NV_WRITE_ITEM:
                return "NV_WRITE_ITEM";
            case RIL_REQUEST_NV_WRITE_CDMA_PRL:
                return "NV_WRITE_CDMA_PRL";
            case RIL_REQUEST_NV_RESET_CONFIG:
                return "NV_RESET_CONFIG";
            case RIL_REQUEST_SET_UICC_SUBSCRIPTION:
                return "SET_UICC_SUBSCRIPTION";
            case RIL_REQUEST_ALLOW_DATA:
                return "ALLOW_DATA";
            case RIL_REQUEST_GET_HARDWARE_CONFIG:
                return "GET_HARDWARE_CONFIG";
            case RIL_REQUEST_SIM_AUTHENTICATION:
                return "SIM_AUTHENTICATION";
            case RIL_REQUEST_GET_DC_RT_INFO:
                return "GET_DC_RT_INFO";
            case RIL_REQUEST_SET_DC_RT_INFO_RATE:
                return "SET_DC_RT_INFO_RATE";
            case RIL_REQUEST_SET_DATA_PROFILE:
                return "SET_DATA_PROFILE";
            case RIL_REQUEST_SHUTDOWN:
                return "SHUTDOWN";
            case RIL_REQUEST_GET_RADIO_CAPABILITY:
                return "GET_RADIO_CAPABILITY";
            case RIL_REQUEST_SET_RADIO_CAPABILITY:
                return "SET_RADIO_CAPABILITY";
            case RIL_REQUEST_START_LCE:
                return "START_LCE";
            case RIL_REQUEST_STOP_LCE:
                return "STOP_LCE";
            case RIL_REQUEST_PULL_LCEDATA:
                return "PULL_LCEDATA";
            case RIL_REQUEST_GET_ACTIVITY_INFO:
                return "GET_ACTIVITY_INFO";
            case RIL_REQUEST_SET_ALLOWED_CARRIERS:
                return "SET_ALLOWED_CARRIERS";
            case RIL_REQUEST_GET_ALLOWED_CARRIERS:
                return "GET_ALLOWED_CARRIERS";
            case RIL_REQUEST_SEND_DEVICE_STATE:
                return "SEND_DEVICE_STATE";
            case RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER:
                return "SET_UNSOLICITED_RESPONSE_FILTER";
            case RIL_REQUEST_SET_SIM_CARD_POWER:
                return "SET_SIM_CARD_POWER";
            case RIL_REQUEST_SET_CARRIER_INFO_IMSI_ENCRYPTION:
                return "SET_CARRIER_INFO_IMSI_ENCRYPTION";
            case RIL_REQUEST_START_NETWORK_SCAN:
                return "START_NETWORK_SCAN";
            case RIL_REQUEST_STOP_NETWORK_SCAN:
                return "STOP_NETWORK_SCAN";
            case RIL_REQUEST_START_KEEPALIVE:
                return "START_KEEPALIVE";
            case RIL_REQUEST_STOP_KEEPALIVE:
                return "STOP_KEEPALIVE";
            case RIL_REQUEST_ENABLE_MODEM:
                return "ENABLE_MODEM";
            case RIL_REQUEST_GET_MODEM_STATUS:
                return "GET_MODEM_STATUS";
            case RIL_REQUEST_CDMA_SEND_SMS_EXPECT_MORE:
                return "CDMA_SEND_SMS_EXPECT_MORE";
            case RIL_REQUEST_GET_SIM_PHONEBOOK_CAPACITY:
                return "GET_SIM_PHONEBOOK_CAPACITY";
            case RIL_REQUEST_GET_SIM_PHONEBOOK_RECORDS:
                return "GET_SIM_PHONEBOOK_RECORDS";
            case RIL_REQUEST_UPDATE_SIM_PHONEBOOK_RECORD:
                return "UPDATE_SIM_PHONEBOOK_RECORD";
            case RIL_REQUEST_GET_SLOT_STATUS:
                return "GET_SLOT_STATUS";
            case RIL_REQUEST_SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING:
                return "SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING";
            case RIL_REQUEST_SET_SIGNAL_STRENGTH_REPORTING_CRITERIA:
                return "SET_SIGNAL_STRENGTH_REPORTING_CRITERIA";
            case RIL_REQUEST_SET_LINK_CAPACITY_REPORTING_CRITERIA:
                return "SET_LINK_CAPACITY_REPORTING_CRITERIA";
            case RIL_REQUEST_SET_PREFERRED_DATA_MODEM:
                return "SET_PREFERRED_DATA_MODEM";
            case RIL_REQUEST_EMERGENCY_DIAL:
                return "EMERGENCY_DIAL";
            case RIL_REQUEST_GET_PHONE_CAPABILITY:
                return "GET_PHONE_CAPABILITY";
            case RIL_REQUEST_SWITCH_DUAL_SIM_CONFIG:
                return "SWITCH_DUAL_SIM_CONFIG";
            case RIL_REQUEST_ENABLE_UICC_APPLICATIONS:
                return "ENABLE_UICC_APPLICATIONS";
            case RIL_REQUEST_GET_UICC_APPLICATIONS_ENABLEMENT:
                return "GET_UICC_APPLICATIONS_ENABLEMENT";
            case RIL_REQUEST_SET_SYSTEM_SELECTION_CHANNELS:
                return "SET_SYSTEM_SELECTION_CHANNELS";
            case RIL_REQUEST_GET_BARRING_INFO:
                return "GET_BARRING_INFO";
            case RIL_REQUEST_ENTER_SIM_DEPERSONALIZATION:
                return "ENTER_SIM_DEPERSONALIZATION";
            case RIL_REQUEST_ENABLE_NR_DUAL_CONNECTIVITY:
                return "ENABLE_NR_DUAL_CONNECTIVITY";
            case RIL_REQUEST_IS_NR_DUAL_CONNECTIVITY_ENABLED:
                return "IS_NR_DUAL_CONNECTIVITY_ENABLED";
            case RIL_REQUEST_ALLOCATE_PDU_SESSION_ID:
                return "ALLOCATE_PDU_SESSION_ID";
            case RIL_REQUEST_RELEASE_PDU_SESSION_ID:
                return "RELEASE_PDU_SESSION_ID";
            case RIL_REQUEST_START_HANDOVER:
                return "START_HANDOVER";
            case RIL_REQUEST_CANCEL_HANDOVER:
                return "CANCEL_HANDOVER";
            case RIL_REQUEST_GET_SYSTEM_SELECTION_CHANNELS:
                return "GET_SYSTEM_SELECTION_CHANNELS";
            case RIL_REQUEST_GET_HAL_DEVICE_CAPABILITIES:
                return "GET_HAL_DEVICE_CAPABILITIES";
            case RIL_REQUEST_SET_DATA_THROTTLING:
                return "SET_DATA_THROTTLING";
            case RIL_REQUEST_SET_ALLOWED_NETWORK_TYPES_BITMAP:
                return "SET_ALLOWED_NETWORK_TYPES_BITMAP";
            case RIL_REQUEST_GET_ALLOWED_NETWORK_TYPES_BITMAP:
                return "GET_ALLOWED_NETWORK_TYPES_BITMAP";
            case RIL_REQUEST_GET_SLICING_CONFIG:
                return "GET_SLICING_CONFIG";
            default: return "<unknown request>";
        }
    }

    /**
     * RIL response to String
     * @param response response
     * @return The converted String response
     */
    public static String responseToString(int response) {
        switch(response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED:
                return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED:
                return "UNSOL_RESPONSE_NETWORK_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_NEW_SMS:
                return "UNSOL_RESPONSE_NEW_SMS";
            case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT:
                return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM:
                return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case RIL_UNSOL_ON_USSD:
                return "UNSOL_ON_USSD";
            case RIL_UNSOL_ON_USSD_REQUEST:
                return "UNSOL_ON_USSD_REQUEST";
            case RIL_UNSOL_NITZ_TIME_RECEIVED:
                return "UNSOL_NITZ_TIME_RECEIVED";
            case RIL_UNSOL_SIGNAL_STRENGTH:
                return "UNSOL_SIGNAL_STRENGTH";
            case RIL_UNSOL_DATA_CALL_LIST_CHANGED:
                return "UNSOL_DATA_CALL_LIST_CHANGED";
            case RIL_UNSOL_SUPP_SVC_NOTIFICATION:
                return "UNSOL_SUPP_SVC_NOTIFICATION";
            case RIL_UNSOL_STK_SESSION_END:
                return "UNSOL_STK_SESSION_END";
            case RIL_UNSOL_STK_PROACTIVE_COMMAND:
                return "UNSOL_STK_PROACTIVE_COMMAND";
            case RIL_UNSOL_STK_EVENT_NOTIFY:
                return "UNSOL_STK_EVENT_NOTIFY";
            case RIL_UNSOL_STK_CALL_SETUP:
                return "UNSOL_STK_CALL_SETUP";
            case RIL_UNSOL_SIM_SMS_STORAGE_FULL:
                return "UNSOL_SIM_SMS_STORAGE_FULL";
            case RIL_UNSOL_SIM_REFRESH:
                return "UNSOL_SIM_REFRESH";
            case RIL_UNSOL_CALL_RING:
                return "UNSOL_CALL_RING";
            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:
                return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:
                return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
                return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:
                return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case RIL_UNSOL_RESTRICTED_STATE_CHANGED:
                return "UNSOL_RESTRICTED_STATE_CHANGED";
            case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE:
                return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case RIL_UNSOL_CDMA_CALL_WAITING:
                return "UNSOL_CDMA_CALL_WAITING";
            case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS:
                return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case RIL_UNSOL_CDMA_INFO_REC:
                return "UNSOL_CDMA_INFO_REC";
            case RIL_UNSOL_OEM_HOOK_RAW:
                return "UNSOL_OEM_HOOK_RAW";
            case RIL_UNSOL_RINGBACK_TONE:
                return "UNSOL_RINGBACK_TONE";
            case RIL_UNSOL_RESEND_INCALL_MUTE:
                return "UNSOL_RESEND_INCALL_MUTE";
            case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
                return "UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case RIL_UNSOL_CDMA_PRL_CHANGED:
                return "UNSOL_CDMA_PRL_CHANGED";
            case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE:
                return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case RIL_UNSOL_RIL_CONNECTED:
                return "UNSOL_RIL_CONNECTED";
            case RIL_UNSOL_VOICE_RADIO_TECH_CHANGED:
                return "UNSOL_VOICE_RADIO_TECH_CHANGED";
            case RIL_UNSOL_CELL_INFO_LIST:
                return "UNSOL_CELL_INFO_LIST";
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
                return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
            case RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED:
                return "UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
            case RIL_UNSOL_SRVCC_STATE_NOTIFY:
                return "UNSOL_SRVCC_STATE_NOTIFY";
            case RIL_UNSOL_HARDWARE_CONFIG_CHANGED:
                return "UNSOL_HARDWARE_CONFIG_CHANGED";
            case RIL_UNSOL_DC_RT_INFO_CHANGED:
                return "UNSOL_DC_RT_INFO_CHANGED";
            case RIL_UNSOL_RADIO_CAPABILITY:
                return "UNSOL_RADIO_CAPABILITY";
            case RIL_UNSOL_ON_SS:
                return "UNSOL_ON_SS";
            case RIL_UNSOL_STK_CC_ALPHA_NOTIFY:
                return "UNSOL_STK_CC_ALPHA_NOTIFY";
            case RIL_UNSOL_LCEDATA_RECV:
                return "UNSOL_LCE_INFO_RECV";
            case RIL_UNSOL_PCO_DATA:
                return "UNSOL_PCO_DATA";
            case RIL_UNSOL_MODEM_RESTART:
                return "UNSOL_MODEM_RESTART";
            case RIL_UNSOL_CARRIER_INFO_IMSI_ENCRYPTION:
                return "UNSOL_CARRIER_INFO_IMSI_ENCRYPTION";
            case RIL_UNSOL_NETWORK_SCAN_RESULT:
                return "UNSOL_NETWORK_SCAN_RESULT";
            case RIL_UNSOL_KEEPALIVE_STATUS:
                return "UNSOL_KEEPALIVE_STATUS";
            case RIL_UNSOL_UNTHROTTLE_APN:
                return "UNSOL_UNTHROTTLE_APN";
            case RIL_UNSOL_RESPONSE_SIM_PHONEBOOK_CHANGED:
                return "UNSOL_RESPONSE_SIM_PHONEBOOK_CHANGED";
            case RIL_UNSOL_RESPONSE_SIM_PHONEBOOK_RECORDS_RECEIVED:
                return "UNSOL_RESPONSE_SIM_PHONEBOOK_RECORDS_RECEIVED";
            case RIL_UNSOL_ICC_SLOT_STATUS:
                return "UNSOL_ICC_SLOT_STATUS";
            case RIL_UNSOL_PHYSICAL_CHANNEL_CONFIG:
                return "UNSOL_PHYSICAL_CHANNEL_CONFIG";
            case RIL_UNSOL_EMERGENCY_NUMBER_LIST:
                return "UNSOL_EMERGENCY_NUMBER_LIST";
            case RIL_UNSOL_UICC_APPLICATIONS_ENABLEMENT_CHANGED:
                return "UNSOL_UICC_APPLICATIONS_ENABLEMENT_CHANGED";
            case RIL_UNSOL_REGISTRATION_FAILED:
                return "UNSOL_REGISTRATION_FAILED";
            case RIL_UNSOL_BARRING_INFO_CHANGED:
                return "UNSOL_BARRING_INFO_CHANGED";
            default:
                return "<unknown response>";
        }
    }
}
