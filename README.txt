-- README for frameworks/opt/telephony --

This directory contains telephony libraries which function as the
implementation code for APIs in TelephonyManager, SubscriptionManager,
SmsManager and others.

These libraries run in the com.android.phone process and exist to support
telephony services created by the user’s apps (generally carrier apps), or by
the system. This includes making phone calls, sending SMS/MMS, and connecting
to data. Many APIs are plumbed down to the radio through HIDL calls defined in
hardware/interfaces/radio/ hardware/interfaces/radio/config and return values
that are sent back up as responses.

We define several AIDL interfaces in frameworks/base/telephony/ which we
implement in this directory and packages/services/Telephony. This IPC scheme
allows us to run public API code in the calling process, while the
telephony-related code runs in the privileged com.android.phone process. Such
implementations include PhoneInterfaceManager, SubscriptionController and
others.

The declaration of the com.android.phone process is in
packages/services/telephony and the top-level application class is PhoneApp,
which initializes everything else.

-- Testing --

Unit tests are found in frameworks/opt/telephony/tests and can be
run on a device connected through ADB with the command:

	atest FrameworksTelephonyTests

Tests can also be run individually or by class:

	atest <testClassName
	atest <testClassName>#<testMethodName>

For more on atest run `atest --help`
