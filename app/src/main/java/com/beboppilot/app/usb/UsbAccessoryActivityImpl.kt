package com.beboppilot.app.usb

import com.beboppilot.app.MainActivity
import com.parrot.arsdk.ardiscovery.UsbAccessoryActivity

class UsbAccessoryActivityImpl : UsbAccessoryActivity() {
    override fun getBaseActivity(): Class<*> = MainActivity::class.java
}
