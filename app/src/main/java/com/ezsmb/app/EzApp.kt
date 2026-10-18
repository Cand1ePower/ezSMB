package com.ezsmb.app

import android.app.Application
import com.ezsmb.app.core.SmbEngine
import com.ezsmb.app.data.DeviceStore
import com.ezsmb.app.data.Discovery
import com.ezsmb.app.transfer.TransferQueue

class EzApp : Application() {
    val engine by lazy { SmbEngine() }
    val store by lazy { DeviceStore(this) }
    val discovery by lazy { Discovery(this, engine) }
    val transfers by lazy { TransferQueue(this, engine) }
    val thumbnails by lazy { com.ezsmb.app.data.Thumbnails(engine) }
}
