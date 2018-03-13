package com.android.internal.telephony;

import android.os.Handler;
import android.os.Message;

public interface AbstractCommandsInterface {
    void clearTrafficData(Message message);

    void closeRrc();

    boolean closeSwitchOfUploadAntOrMaxTxPower(int i);

    void closeSwitchOfUploadBandClass(Message message);

    boolean cmdForECInfo(int i, int i2, byte[] bArr);

    void dataConnectionAttach(int i, Message message);

    void dataConnectionDetach(int i, Message message);

    void fastSwitchBalongSim(int i, int i2, Message message);

    void getAvailableCSGNetworks(Message message);

    void getAvailableCSGNetworks(byte[] bArr, Message message);

    void getBalongSim(Message message);

    void getCdmaChrInfo(Message message);

    void getCdmaModeSide(Message message);

    void getCurrentPOLList(Message message);

    void getDevSubMode(Message message);

    String getHwCDMAMlplVersion();

    String getHwCDMAMsplVersion();

    String getHwPrlVersion();

    void getHwRatCombineMode(Message message);

    String getHwUimid();

    void getICCID(Message message);

    void getImsDomain(Message message);

    boolean getImsSwitch();

    void getLaaDetailedState(String str, Message message);

    void getLocationInfo(Message message);

    void getLteFreqWithWlanCoex(Message message);

    void getLteReleaseVersion(Message message);

    void getModemCapability(Message message);

    void getModemSupportVSimVersion(Message message);

    String getNVESN();

    void getPOLCapabilty(Message message);

    int getRILid();

    void getRcsSwitchState(Message message);

    void getRegPlmn(Message message);

    void getSimHotPlugState(Message message);

    void getSimMode(Message message);

    void getSimState(Message message);

    void getSimStateViaSysinfoEx(Message message);

    void getTrafficData(Message message);

    void handleMapconImsaReq(byte[] bArr, Message message);

    void handleUiccAuth(int i, byte[] bArr, byte[] bArr2, Message message);

    void hotSwitchSimSlot(int i, int i2, int i3, Message message);

    void hotSwitchSimSlotFor2Modem(int i, int i2, int i3, Message message);

    void hvCheckCard(Message message);

    void iccGetATR(Message message);

    boolean isFastSwitchInProcess();

    boolean isRadioAvailable();

    void notifyAntOrMaxTxPowerInfo(byte[] bArr);

    void notifyBandClassInfo(byte[] bArr);

    void notifyCModemStatus(int i, Message message);

    void notifyCellularCommParaReady(int i, int i2, Message message);

    void notifyDeviceState(String str, String str2, String str3, Message message);

    boolean openSwitchOfUploadAntOrMaxTxPower(int i);

    void openSwitchOfUploadBandClass(Message message);

    void processHWBufferUnsolicited(byte[] bArr);

    void queryCardType(Message message);

    void queryEmergencyNumbers();

    void queryServiceCellBand(Message message);

    void registerCommonImsaToMapconInfo(Handler handler, int i, Object obj);

    void registerForCaStateChanged(Handler handler, int i, Object obj);

    void registerForCallAltSrv(Handler handler, int i, Object obj);

    void registerForCrrConn(Handler handler, int i, Object obj);

    void registerForHWBuffer(Handler handler, int i, Object obj);

    void registerForIccidChanged(Handler handler, int i, Object obj);

    void registerForLaaStateChange(Handler handler, int i, Object obj);

    void registerForLimitPDPAct(Handler handler, int i, Object obj);

    void registerForModemCapEvent(Handler handler, int i, Object obj);

    void registerForReportVpStatus(Handler handler, int i, Object obj);

    void registerForRplmnsStateChanged(Handler handler, int i, Object obj);

    void registerForSimHotPlug(Handler handler, int i, Object obj);

    void registerForSimSwitchStart(Handler handler, int i, Object obj);

    void registerForSimSwitchStop(Handler handler, int i, Object obj);

    void registerForUimLockcard(Handler handler, int i, Object obj);

    void registerForUnsolBalongModemReset(Handler handler, int i, Object obj);

    void registerForUnsolSpeechInfo(Handler handler, int i, Object obj);

    boolean registerSarRegistrant(int i, Message message);

    void rejectCallForCause(int i, int i2, Message message);

    void requestSetEmergencyNumbers(String str, String str2);

    void resetAllConnections();

    void resetProfile(Message message);

    void restartRild(Message message);

    void riseCdmaCutoffFreq(boolean z, Message message);

    void sendCloudMessageToModem(int i);

    void sendHWBufferSolicited(Message message, int i, byte[] bArr);

    void sendLaaCmd(int i, String str, Message message);

    void sendPseudocellCellInfo(int i, int i2, int i3, int i4, String str, Message message);

    void sendSMSSetLong(int i, Message message);

    void setActiveModemMode(int i, Message message);

    void setApDsFlowCfg(int i, int i2, int i3, int i4, Message message);

    void setCSGNetworkSelectionModeManual(Object obj, Message message);

    void setCSGNetworkSelectionModeManual(byte[] bArr, Message message);

    void setCdmaModeSide(int i, Message message);

    void setDmPcscf(String str, Message message);

    void setDmRcsConfig(int i, int i2, Message message);

    void setDsFlowNvCfg(int i, int i2, Message message);

    boolean setEhrpdByQMI(boolean z);

    void setFastSimSwitchInProcess(boolean z, Message message);

    void setHwRFChannelSwitch(int i, Message message);

    void setHwRatCombineMode(int i, Message message);

    void setHwVSimPower(int i, Message message);

    void setISMCOEX(String str, Message message);

    void setImsDomainConfig(int i, Message message);

    void setImsSwitch(boolean z);

    void setLTEReleaseVersion(int i, Message message);

    void setMobileDataEnable(int i, Message message);

    void setNetworkRatAndSrvDomainCfg(int i, int i2, Message message);

    void setOnECCNum(Handler handler, int i, Object obj);

    void setOnNetReject(Handler handler, int i, Object obj);

    void setOnRegPLMNSelInfo(Handler handler, int i, Object obj);

    void setOnRestartRildNvMatch(Handler handler, int i, Object obj);

    void setOnVsimApDsFlowInfo(Handler handler, int i, Object obj);

    void setOnVsimDsFlowInfo(Handler handler, int i, Object obj);

    void setOnVsimRDH(Handler handler, int i, Object obj);

    void setOnVsimRegPLMNSelInfo(Handler handler, int i, Object obj);

    void setOnVsimTimerTaskExpired(Handler handler, int i, Object obj);

    void setPOLEntry(int i, String str, int i2, Message message);

    void setPowerGrade(int i, Message message);

    void setRcsSwitch(int i, Message message);

    void setRoamingDataEnable(int i, Message message);

    void setSimMode(int i, int i2, int i3, Message message);

    void setSimState(int i, int i2, Message message);

    void setTEEDataReady(int i, int i2, int i3, Message message);

    void setUEOperationMode(int i, Message message);

    void setVpMask(int i, Message message);

    void setWifiTxPowerGrade(int i, Message message);

    void supplyDepersonalization(String str, int i, Message message);

    void switchBalongSim(int i, int i2, int i3, Message message);

    void switchBalongSim(int i, int i2, Message message);

    void switchVoiceCallBackgroundState(int i, Message message);

    void unSetOnECCNum(Handler handler);

    void unSetOnNetReject(Handler handler);

    void unSetOnRegPLMNSelInfo(Handler handler);

    void unSetOnRestartRildNvMatch(Handler handler);

    void unSetOnVsimApDsFlowInfo(Handler handler);

    void unSetOnVsimDsFlowInfo(Handler handler);

    void unSetOnVsimRDH(Handler handler);

    void unSetOnVsimRegPLMNSelInfo(Handler handler);

    void unSetOnVsimTimerTaskExpired(Handler handler);

    void unregisterCommonImsaToMapconInfo(Handler handler);

    void unregisterForCaStateChanged(Handler handler);

    void unregisterForCallAltSrv(Handler handler);

    void unregisterForCrrConn(Handler handler);

    void unregisterForHWBuffer(Handler handler);

    void unregisterForIccidChanged(Handler handler);

    void unregisterForLaaStateChange(Handler handler);

    void unregisterForLimitPDPAct(Handler handler);

    void unregisterForModemCapEvent(Handler handler);

    void unregisterForReportVpStatus(Handler handler);

    void unregisterForRplmnsStateChanged(Handler handler);

    void unregisterForSimHotPlug(Handler handler);

    void unregisterForSimSwitchStart(Handler handler);

    void unregisterForSimSwitchStop(Handler handler);

    void unregisterForUimLockcard(Handler handler);

    void unregisterForUnsolBalongModemReset(Handler handler);

    void unregisterForUnsolSpeechInfo(Handler handler);

    boolean unregisterSarRegistrant(int i, Message message);

    boolean updateSocketMapForSlaveSub(int i, int i2, int i3);

    void updateStackBinding(int i, int i2, Message message);
}
