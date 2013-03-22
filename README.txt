This package contains classes used to manage a DataConnection.

A criticial aspect of this class is that most objects in this
package run on the same thread except DataConnectionTracker
This makes processing efficient as it minimizes context
switching and it eliminates issues with multi-threading.

This can be done because all actions are either asynchronous
or are known to be non-blocking and fast. At this time only
DcTesterDeactivateAll takes specific advantage of this
single threading knowledge by using Dcc#mDcListAll so be
very careful when making changes that break this assumption.

A related change was in DataConnectionAc I added code that
checks to see if the caller is on a different thread. If
it is then the AsyncChannel#sendMessageSynchronously is
used. If the caller is on the same thread then a getter
is used. This allows the DCAC to be used from any thread
and was required to fix a bug when Dcc called
PhoneBase#notifyDataConnection which calls DCT#getLinkProperties
and DCT#getLinkCapabilities which call Dcc all on the same
thread. Without this change there was a dead lock when
sendMessageSynchronously blocks.


Testing:

There are three Intents that can be sent for testing pruproses:

The first two cause bringUp and retry requests to fail and the first
causes all DC's to fail the second causes a specific DC to fail:

  adb shell am broadcast -a com.android.internal.telephony.dataconnection.action_fail_bringup --ei counter 2 --ei fail_cause -3
  adb shell am broadcast -a com.android.internal.telephony.dataconnection.DC-1.action_fail_bringup --ei counter 2 --ei fail_cause -3

The other causes all DC's to get torn down, simulating a temporary network outage:

  adb shell am broadcast -a com.android.internal.telephony.dataconnection.action_deactivate_all

To simplify testing we also have detach and attach simulations below where {x} is gsm, cdma or sip

  adb shell am broadcast -a com.android.internal.telephony.{x}.action_detached
  adb shell am broadcast -a com.android.internal.telephony.{x}.action_attached


Additionally, you on DEGUGGABLE builds (userdebug, eng) you can replace the retry configuration
by setting the SystemProperty: test.data_retry_config for instance:

  adb shell setprop test.data_retry_config "5000,5000,5000"

Which changes the retry to 3 retires at 5 second intervals. This can be added to
/data/local.prop, don't forget to "adb shell chmod 0600 /data/local.prop":
  $ cat local.prop.test.data_retry_config
  test.data_retry_config=5000,5000,5000,5000

