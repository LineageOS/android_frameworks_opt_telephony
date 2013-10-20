package com.android.internal.telephony.cdma;

public class CdmaERIInfo {
    public int carrier_id;
    public int data_support;
    public int eri_id;
    public int icon_img_id;
    public int param1;
    public int param2;
    public int param3;
    public int param4;
    public int roaming_type;
    public String text;

    public String toString() {
        return "com.android.internal.telephony.cdma.CdmaERIInfo: { carrier_id: " + this.carrier_id + ", eri_id: " + this.eri_id + ", icon_img_id: " + this.icon_img_id + ", param1:" + this.param1 + ", param2: " + this.param2 + ", param3: " + this.param3 + ", param4: " + this.param4 + ", text: " + this.text + ", data_support: " + this.data_support + ", roaming_type: " + this.roaming_type + " }";
    }
}