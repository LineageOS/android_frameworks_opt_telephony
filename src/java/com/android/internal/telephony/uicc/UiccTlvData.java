package com.android.internal.telephony.uicc;

/**
 * UICC TLV Data Parser according to ETSI TS 102 221 spec.
 */
public class UiccTlvData {

    private static final int TLV_FORMAT_ID = 0x62;

    private static final int TAG_FILE_DESCRIPTOR = 0x82;
    private static final int TAG_FILE_IDENTIFIER = 0x83;
    private static final int TAG_PROPRIETARY_INFO = 0xA5;
    private static final int TAG_LIFECYCLE_STATUS = 0x8A;
    private static final int TAG_SECURITY_ATTR_1 = 0x8B;
    private static final int TAG_SECURITY_ATTR_2 = 0x8C;
    private static final int TAG_SECURITY_ATTR_3 = 0xAB;
    private static final int TAG_FILE_SIZE = 0x80;
    private static final int TAG_TOTAL_FILE_SIZE = 0x81;
    private static final int TAG_SHORT_FILE_IDENTIFIER = 0x88;

    private static final int TYPE_5 = 5;
    private static final int TYPE_2 = 2;

    int mRecordSize;
    int mFileSize;
    int mNumRecords;
    boolean mIsDataEnough;

    private int mFileType = -1;

    private UiccTlvData() {
        mNumRecords = -1;
        mFileSize = -1;
        mRecordSize = -1;
    }

    public boolean isIncomplete() {
        return mNumRecords == -1 || mFileSize == -1 || mRecordSize == -1 || mFileType == -1;
    }

    public static boolean isUiccTlvData(byte[] data) {
        if(data != null && data.length > 0 && TLV_FORMAT_ID == (data[0] & 0xFF)) {
            return true;
        }
        return false;
    }

    public static UiccTlvData parse(byte[] data) throws IccFileTypeMismatch{

        UiccTlvData parsedData = new UiccTlvData();

        if (data == null || data.length == 0 || TLV_FORMAT_ID != (data[0] & 0xFF)) {
            throw new IccFileTypeMismatch();
        }

        try {

            int currentLocation = 2; //Ignore FCP size
            int currentTag;

            while (currentLocation < data.length) {
                currentTag = data[currentLocation++] & 0xFF;

                switch (currentTag) {
                    case TAG_FILE_DESCRIPTOR:
                        currentLocation = parsedData.parseFileDescriptor(data, currentLocation);
                        break;

                    case TAG_FILE_SIZE:
                        currentLocation = parsedData.parseFileSize(data, currentLocation);
                        break;

                    case TAG_FILE_IDENTIFIER:
                    case TAG_PROPRIETARY_INFO:
                    case TAG_LIFECYCLE_STATUS:
                    case TAG_SECURITY_ATTR_1:
                    case TAG_SECURITY_ATTR_2:
                    case TAG_SECURITY_ATTR_3:
                    case TAG_TOTAL_FILE_SIZE:
                    case TAG_SHORT_FILE_IDENTIFIER:
                        currentLocation = parsedData.parseSomeTag(data, currentLocation);
                        break;

                    default:
                        //Unknown TAG
                        throw new IccFileTypeMismatch();

                }
            }

        } catch (ArrayIndexOutOfBoundsException e) {

            //We might be looking at incomplete data but we might have what we need.
            //Ignore this  and let caller handle it by checking isIncomplete
        }

        return parsedData;
    }

    private int parseFileSize(byte[] data, int currentLocation) {
        int length = data[currentLocation++] & 0xFF;

        int fileSize = 0;
        for (int i = 0; i < length; i++) {
            fileSize += ((data[currentLocation + i] & 0xFF) << ( 8 * (length - i - 1)));
        }

        mFileSize = fileSize;

        if (mFileType == TYPE_2) {
            mRecordSize = fileSize;
        }

        return currentLocation + length;
    }

    private int parseSomeTag(byte[] data, int currentLocation) {
        //Just skip unwanted tags;
        int length = data[currentLocation++] & 0xFF;
        return currentLocation + length;
    }

    private int parseFileDescriptor(byte[] data, int currentLocation) throws IccFileTypeMismatch {
        int length = data[currentLocation++] & 0xFF;
        if (length == 5) {

            mRecordSize = ((data[currentLocation + 2] & 0xFF) << 8) +
                    (data[currentLocation + 3] & 0xFF); // Length of 1 record
            mNumRecords = data[currentLocation + 4] & 0xFF; // Number of records

            mFileSize = mRecordSize * mNumRecords;

            mFileType = TYPE_5;

            return currentLocation + 5;
        } else if (length == 2) {

            int descriptorByte = data[currentLocation + 1] & 0xFF;

            //Ignore descriptorByte for now

            mNumRecords = 1;

            mFileType = TYPE_2;

            return currentLocation + 2;

        } else {
            throw new IccFileTypeMismatch();
        }
    }

}

