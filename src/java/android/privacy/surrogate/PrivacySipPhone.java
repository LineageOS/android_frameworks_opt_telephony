/**
* Copyright (C) 2012 Stefan Thiele
* This program is free software; you can redistribute it and/or modify it under
* the terms of the GNU General Public License as published by the Free Software
* Foundation; either version 3 of the License, or (at your option) any later version.
* This program is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* this program; if not, see <http://www.gnu.org/licenses>.
*/

package android.privacy.surrogate;

import android.content.Context;
import android.net.sip.SipProfile;
import android.os.Binder;
import android.os.Process;
import android.os.ServiceManager;
import android.privacy.IPrivacySettingsManager;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.sip.SipPhone;
/**
 * Provides privacy handling for phone
 * @author CollegeDev
 * {@hide}
 */
public class PrivacySipPhone extends SipPhone{

	private static final String P_TAG = "PrivacyGSMPhone";
	
	private PrivacySettingsManager pSetMan;
	
	private Context context;
	
	public PrivacySipPhone(Context context, PhoneNotifier pN, SipProfile sP) {
		super(context, pN, sP); //I've changed the constructor to public!
		this.context = context;
		pSetMan = new PrivacySettingsManager(context, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));
		Log.i(P_TAG,"Constructor ready for package: " + context.getPackageName());
	}
	
	@Override
	public String getDeviceSvn() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getDeviceSvn()");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getDeviceIdSetting() != PrivacySettings.REAL) {
            output = pSet.getDeviceId(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getDeviceIdSetting(), PrivacySettings.DATA_DEVICE_ID, output, pSet);
        } else {
            output = super.getDeviceId();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_DEVICE_ID, output, pSet);
        }
        return output;
	}
	
	@Override
	public String getImei() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getImei");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getDeviceIdSetting() != PrivacySettings.REAL) {
            output = pSet.getDeviceId(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getDeviceIdSetting(), PrivacySettings.DATA_DEVICE_ID, output, pSet);
        } else {
            output = super.getImei();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_DEVICE_ID, output, pSet);
        }
        return output;
	}
	
	@Override
	public String getSubscriberId() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getSubscriberId()");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getSubscriberIdSetting() != PrivacySettings.REAL) {
            output = pSet.getSubscriberId(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getSubscriberIdSetting(), PrivacySettings.DATA_SUBSCRIBER_ID, output, pSet);            
        } else {
            output = super.getSubscriberId();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_SUBSCRIBER_ID, output, pSet);            
        }
        return output;
	}
	

//	void notifyLocationChanged() {
//		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for notifyLocationChanged()");
//		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Process.myUid());
//		if(pSetMan != null && settings != null && settings.getNetworkInfoSetting() != PrivacySettings.REAL){
//			//do nothing here
//		}
//		else
//			mNotifier.notifyCellLocation(this);
//	}
	
	@Override
	public String getLine1AlphaTag() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getLine1AlphaTag()");
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Process.myUid());
		String output = "";
		if(pSetMan != null && settings != null && settings.getLine1NumberSetting() != PrivacySettings.REAL){
			output = settings.getLine1Number();
			pSetMan.notification(context.getPackageName(), 0, settings.getLine1NumberSetting(), PrivacySettings.DATA_LINE_1_NUMBER, output, settings);
		}
		else{
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.REAL, PrivacySettings.DATA_LINE_1_NUMBER, output, settings);
			output = super.getLine1AlphaTag();
		}
		return output;
	}
	
	@Override
	public String getVoiceMailAlphaTag() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getVoiceMailAlphaTag()");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getLine1NumberSetting() != PrivacySettings.REAL) {
            output = pSet.getLine1Number(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getLine1NumberSetting(), PrivacySettings.DATA_LINE_1_NUMBER, output, pSet);
        } else {
            output = super.getVoiceMailAlphaTag();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LINE_1_NUMBER, output, pSet);
        }
        return output;
	}
	
	@Override
	public String getVoiceMailNumber(){
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getVoiceMailNumber()");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getLine1NumberSetting() != PrivacySettings.REAL) {
            output = pSet.getLine1Number(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getLine1NumberSetting(), PrivacySettings.DATA_LINE_1_NUMBER, output, pSet);
        } else {
            output = super.getVoiceMailNumber();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_LINE_1_NUMBER, output, pSet);
        }
        return output;
	}

	@Override
	public String getDeviceId() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getDeviceId()");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getDeviceIdSetting() != PrivacySettings.REAL) {
            output = pSet.getDeviceId(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getDeviceIdSetting(), PrivacySettings.DATA_DEVICE_ID, output, pSet);
        } else {
            output = super.getDeviceId();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_DEVICE_ID, output, pSet);
        }
        return output;
	}
	
	@Override
	public String getMeid() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getMeid()");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getDeviceIdSetting() != PrivacySettings.REAL) {
            output = pSet.getDeviceId(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getDeviceIdSetting(), PrivacySettings.DATA_DEVICE_ID, output, pSet);
        } else {
            output = super.getMeid();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_DEVICE_ID, output, pSet);
        }
        return output;
	}
	
	@Override
	public String getEsn() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getEsn()");
		String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output;
        if (pSet != null && pSet.getDeviceIdSetting() != PrivacySettings.REAL) {
            output = pSet.getDeviceId(); // can be empty, custom or random
            pSetMan.notification(packageName, uid, pSet.getDeviceIdSetting(), PrivacySettings.DATA_DEVICE_ID, output, pSet);
        } else {
            output = super.getEsn();
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_DEVICE_ID, output, pSet);
        }
        return output;
	}
	
	@Override
	public String getLine1Number() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getLine1Number()");
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Process.myUid());
		String output = "";
		if(pSetMan != null && settings != null && settings.getLine1NumberSetting() != PrivacySettings.REAL){
			output = settings.getLine1Number();
			pSetMan.notification(context.getPackageName(), 0, settings.getLine1NumberSetting(), PrivacySettings.DATA_LINE_1_NUMBER, output, settings);
		}
		else{
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.REAL, PrivacySettings.DATA_LINE_1_NUMBER, output, settings);
			output = super.getLine1Number();
		}
		return output;
	}
	
	@Override
	public CellLocation getCellLocation() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getCellLocation()");
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Process.myUid());
		if(pSetMan != null && settings != null && (settings.getLocationGpsSetting() != PrivacySettings.REAL || settings.getLocationNetworkSetting() != PrivacySettings.REAL)){
			pSetMan.notification(context.getPackageName(), 0, settings.getLocationNetworkSetting(), PrivacySettings.DATA_LOCATION_NETWORK, null, settings);
			return new GsmCellLocation();
		}
		else{
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.REAL,PrivacySettings.DATA_LOCATION_NETWORK, null, settings);
			return super.getCellLocation();
		}
	}
	
	@Override
	public PhoneSubInfo getPhoneSubInfo() {
		Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getPhoneSubInfo()");
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Process.myUid());
		if(pSetMan != null && settings != null && settings.getNetworkInfoSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(), 0, settings.getLocationNetworkSetting(), PrivacySettings.DATA_LOCATION_NETWORK, null, settings);
			return null;
		}
		else{
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_NETWORK, null, settings);
			return super.getPhoneSubInfo();
		}
	}
	
	@Override
	public ServiceState getServiceState() {
		try{
			Log.i(P_TAG,"Package: " + context.getPackageName() + " asked for getServiceState()");
			PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), Process.myUid());
			if(pSetMan != null && settings != null && settings.getNetworkInfoSetting() != PrivacySettings.REAL){
				pSetMan.notification(context.getPackageName(), 0, settings.getLocationNetworkSetting(), PrivacySettings.DATA_LOCATION_NETWORK, null, settings);
				ServiceState output = super.getServiceState();
				output.setOperatorName("", "", "");
				return output;
			}
			else{
				pSetMan.notification(context.getPackageName(), 0, PrivacySettings.REAL, PrivacySettings.DATA_LOCATION_NETWORK, null, settings);
				return super.getServiceState();
			}
		}
		catch(Exception e){
			e.printStackTrace();
			Log.e(P_TAG,"We got exception in getServiceState()-> give fake state");
			ServiceState output = super.getServiceState();
			output.setOperatorName("", "", "");
			return output;
		}
		
	}
	
	@Override
    public Connection dial(String dialNumber) throws CallStateException{
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), -1);
		if(pSetMan != null && settings != null && settings.getPhoneCallSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.EMPTY, PrivacySettings.DATA_PHONE_CALL, null, null);
			throw new CallStateException();
		}
		else{
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.REAL, PrivacySettings.DATA_PHONE_CALL, null, null);
			return super.dial(dialNumber);
		}
    }
	
	@Override
    public Connection dial (String dialNumber, UUSInfo uusInfo) throws CallStateException{
		PrivacySettings settings = pSetMan.getSettings(context.getPackageName(), -1);
		if(pSetMan != null && settings != null && settings.getPhoneCallSetting() != PrivacySettings.REAL){
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.EMPTY, PrivacySettings.DATA_PHONE_CALL, null, null);
			throw new CallStateException();
		}
		else{
			pSetMan.notification(context.getPackageName(), 0, PrivacySettings.REAL, PrivacySettings.DATA_PHONE_CALL, null, null);
			return super.dial(dialNumber, uusInfo);
		}
	}

}
