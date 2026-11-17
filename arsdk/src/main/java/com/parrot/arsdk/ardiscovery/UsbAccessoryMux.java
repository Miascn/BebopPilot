/*
    Copyright (C) 2016 Parrot SA

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions
    are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in
      the documentation and/or other materials provided with the
      distribution.
    * Neither the name of Parrot nor the names
      of its contributors may be used to endorse or promote products
      derived from this software without specific prior written
      permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
    "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
    LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
    FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
    COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
    INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
    BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
    OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
    AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
    OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
    OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
    SUCH DAMAGE.
*/

package com.parrot.arsdk.ardiscovery;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.parrot.mux.Mux;

import java.io.IOException;

/**
 * Singleton that hold usb accessory mux instance
 */
public class UsbAccessoryMux {

    public static final String ACTION_USB_ACCESSORY_ATTACHED = "com.parrot.arsdk.USB_ACCESSORY_ATTACHED";
    private static final String ACTION_USB_PERMISSION = "com.parrot.arsdk.USB_ACCESSORY_PERMISSION";

    private static final String MANUFACTURER_ID = "Parrot";
    private static final String SKYCONTROLLER_NG_MODEL_ID = "Skycontroller";
    private static final String SKYCONTROLLER2_MODEL_ID = "Skycontroller 2";
    private static final String SKYCONTROLLER2P_MODEL_ID = "Skycontroller 2P";

    private static final String TAG = "UsbAccessoryMux";
    private static UsbAccessoryMux sInstance;

    public enum MuxState {
        DISCONNECTED,
        PERMISSION_PENDING,
        PERMISSION_DENIED,
        MUX_INITIALIZING,
        MUX_CONNECTED,
        MUX_ERROR
    }

    public interface MuxStateListener {
        void onMuxStateChanged(MuxState state, UsbAccessory accessory, String detail);
    }

    private final Context context;
    // mux
    private final UsbManager usbManager;
    private Mux usbMux;
    private ParcelFileDescriptor muxFileDescriptor;
    private Thread muxThread;
    private UsbAccessory activeAccessory;
    private MuxState muxState = MuxState.DISCONNECTED;
    private String lastDetail;
    private long muxGeneration = 0L;
    private MuxStateListener muxStateListener;
    // discovery
    private ARDiscoveryMux discoveryChannel;
    private ARDiscoveryMux.Listener mDiscoveryListener;
    private final Handler mHandler;

    public static UsbAccessoryMux get(Context appContext) {
        synchronized (UsbAccessoryMux.class) {
            if (sInstance == null) {
                sInstance = new UsbAccessoryMux(appContext.getApplicationContext());
            }
            return sInstance;
        }
    }

    private UsbAccessoryMux(Context appContext) {
        Log.i(TAG, "create UsbAccessoryMux");
        this.context = appContext;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());

        IntentFilter filter = new IntentFilter(ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        ContextCompat.registerReceiver(context, mUsbAccessoryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        refreshAccessories(true);
    }

    public void setMuxStateListener(MuxStateListener listener) {
        final MuxState currentState;
        final UsbAccessory currentAcc;
        final String currentDetail;
        synchronized (this) {
            this.muxStateListener = listener;
            currentState = this.muxState;
            currentAcc = this.activeAccessory;
            currentDetail = this.lastDetail;
        }
        if (listener != null) {
            listener.onMuxStateChanged(currentState, currentAcc, currentDetail);
        }
    }

    private void notifyMuxState(MuxState state, UsbAccessory accessory, String detail) {
        final MuxStateListener listenerCopy;
        synchronized (this) {
            this.muxState = state;
            this.activeAccessory = accessory;
            this.lastDetail = detail;
            listenerCopy = this.muxStateListener;
        }
        if (listenerCopy != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    listenerCopy.onMuxStateChanged(state, accessory, detail);
                }
            });
        }
    }

    public void refreshAccessories() {
        refreshAccessories(false);
    }

    public void requestUsbPermission() {
        refreshAccessories(true);
    }

    public synchronized boolean isHealthy() {
        if (usbManager == null || usbMux == null || !usbMux.isValid()) {
            return false;
        }
        if (muxThread == null || !muxThread.isAlive()) {
            return false;
        }
        if (discoveryChannel == null || !discoveryChannel.isValid()) {
            return false;
        }
        return findSupportedAccessory() != null;
    }

    public synchronized boolean hasSupportedAccessory() {
        return usbManager != null && findSupportedAccessory() != null;
    }

    public synchronized boolean hasAccessoryPermission() {
        if (usbManager == null) {
            return false;
        }
        UsbAccessory matched = findSupportedAccessory();
        return matched != null && usbManager.hasPermission(matched);
    }

    public synchronized void forceReopenMux(boolean requestPermissionIfMissing) {
        Log.i(TAG, "Forcing clean USB MUX reopen (requestPermission=" + requestPermissionIfMissing + ")");
        closeMuxInternal(false);
        refreshAccessories(requestPermissionIfMissing);
    }

    public synchronized void refreshAccessories(boolean requestPermissionIfMissing) {
        if (usbManager == null) {
            notifyMuxState(MuxState.MUX_ERROR, null, "UsbManager no disponible en este dispositivo");
            return;
        }
        if (usbMux != null) {
            if (isHealthy()) {
                notifyMuxState(MuxState.MUX_CONNECTED, activeAccessory, lastDetail);
                return;
            }
            Log.w(TAG, "Detected stale/unhealthy USB MUX during refresh; resetting channel");
            closeMuxInternal(false);
        }

        UsbAccessory matched = findSupportedAccessory();
        if (matched == null) {
            notifyMuxState(MuxState.DISCONNECTED, null, null);
            return;
        }

        if (usbManager.hasPermission(matched)) {
            startMux(matched);
        } else if (requestPermissionIfMissing || muxState == MuxState.DISCONNECTED || muxState == MuxState.MUX_ERROR) {
            notifyMuxState(MuxState.PERMISSION_PENDING, matched, "Solicitando permiso USB Accessory");
            usbManager.requestPermission(matched, createUsbPermissionPendingIntent(context));
        }
    }

    private UsbAccessory findSupportedAccessory() {
        UsbAccessory[] accessoryList = usbManager.getAccessoryList();
        if (accessoryList != null) {
            for (UsbAccessory accessory : accessoryList) {
                if (MANUFACTURER_ID.equals(accessory.getManufacturer()) && isValidModel(accessory.getModel())) {
                    return accessory;
                }
            }
        }
        return null;
    }

    private PendingIntent createUsbPermissionPendingIntent(Context ctx) {
        Intent permIntent = new Intent(ACTION_USB_PERMISSION);
        permIntent.setPackage(ctx.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, 0, permIntent, flags);
    }

    public void setDiscoveryListener(ARDiscoveryMux.Listener listener) {
        mDiscoveryListener = listener;
        if (discoveryChannel != null) {
            discoveryChannel.setListener(mDiscoveryListener);
        }
    }

    public int connect(String device, String model, String id, String json, ARDiscoveryMux.ConnectCallback callback) {
        if (discoveryChannel != null) {
            return discoveryChannel.connect(device, model, id, json, callback);
        }
        return -1;
    }

    public void cancelConnect() {
        if (discoveryChannel != null) {
            discoveryChannel.cancelConnect();
        }
    }

    private synchronized void startMux(UsbAccessory accessory) {
        Log.i(TAG, "Accessory connected " + accessory);
        if (usbMux == null) {
            notifyMuxState(MuxState.MUX_INITIALIZING, accessory, "Abriendo descriptor USB Accessory");
            muxFileDescriptor = usbManager.openAccessory(accessory);
            if (muxFileDescriptor != null) {
                int fd = muxFileDescriptor.getFd();
                Log.i(TAG, "Opening mux, fd=" + fd);
                final long generation = ++muxGeneration;
                final Mux localMux = new Mux(muxFileDescriptor, new Mux.IOnClosedListener() {
                    @Override
                    public void onClosed() {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                closeMuxIfGeneration(generation);
                            }
                        });
                    }
                });
                if (localMux.isValid()) {
                    usbMux = localMux;
                    // start mux thread
                    muxThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            localMux.runReader();
                        }
                    }, "muxThread");
                    muxThread.start();
                    discoveryChannel = new ARDiscoveryMux(localMux);
                    if (mDiscoveryListener != null) {
                        discoveryChannel.setListener(mDiscoveryListener);
                    }
                    notifyMuxState(MuxState.MUX_CONNECTED, accessory, "MUX activo (fd=" + fd + ")");
                } else {
                    Log.e(TAG, "Error opening usb mux");
                    localMux.destroy();
                    usbMux = null;
                    try {
                        muxFileDescriptor.close();
                    } catch (IOException e) {
                    }
                    muxFileDescriptor = null;
                    notifyMuxState(MuxState.MUX_ERROR, accessory, "Mux.isValid() devolvio false");
                }
            } else {
                Log.e(TAG, "Error opening USB Accessory");
                notifyMuxState(MuxState.MUX_ERROR, accessory, "usbManager.openAccessory() devolvio null");
            }
        }
    }

    private synchronized void closeMux() {
        closeMuxInternal(true);
    }

    private synchronized void closeMuxIfGeneration(long generation) {
        if (generation != this.muxGeneration) {
            Log.d(TAG, "Ignoring stale onClosed for generation=" + generation + " (current=" + this.muxGeneration + ")");
            return;
        }
        closeMuxInternal(true);
    }

    private synchronized void closeMuxInternal(boolean notifyDisconnected) {
        muxGeneration++;
        if (discoveryChannel != null) {
            discoveryChannel.destroy();
            discoveryChannel = null;
        }
        if (usbMux != null) {
            usbMux.stop();
        }
        if (muxFileDescriptor != null) {
            try {
                muxFileDescriptor.close();
            } catch (IOException e) {
            }
            muxFileDescriptor = null;
        }
        if (muxThread != null && Thread.currentThread() != muxThread) {
            try {
                muxThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            muxThread = null;
        }
        if (usbMux != null) {
            usbMux.destroy();
            usbMux = null;
        }
        if (notifyDisconnected) {
            UsbAccessory remaining = (usbManager != null) ? findSupportedAccessory() : null;
            notifyMuxState(MuxState.DISCONNECTED, remaining, remaining != null ? "Canal MUX cerrado" : "USB desconectado");
        }
    }

    private BroadcastReceiver mUsbAccessoryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbAccessory accessory = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_ACCESSORY, UsbAccessory.class);
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                Log.i(TAG, "UsbAccessoryReceiver detected USB accessory detached: " + accessory);
                if (accessory == null || (MANUFACTURER_ID.equals(accessory.getManufacturer()) && isValidModel(accessory.getModel()))) {
                    closeMux();
                }
                return;
            }
            if (accessory == null) {
                accessory = findSupportedAccessory();
            }
            if (accessory == null) {
                return;
            }
            String accessoryModel = accessory.getModel();
            if (!MANUFACTURER_ID.equals(accessory.getManufacturer()) || !isValidModel(accessoryModel)) {
                return;
            }

            UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (ACTION_USB_PERMISSION.equals(action)) {
                boolean permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                Log.i(TAG, "UsbAccessoryReceiver permission result for " + accessory + ": " + permissionGranted);
                if (permissionGranted && manager.hasPermission(accessory)) {
                    startMux(accessory);
                } else {
                    notifyMuxState(MuxState.PERMISSION_DENIED, accessory, "Permiso USB denegado por el usuario");
                }
                return;
            }

            if (ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                Log.i(TAG, "UsbAccessoryReceiver attached for accessory " + accessory);
                if (usbMux == null) {
                    if (manager.hasPermission(accessory)) {
                        startMux(accessory);
                    } else {
                        notifyMuxState(MuxState.PERMISSION_PENDING, accessory, "Solicitando permiso USB Accessory");
                        manager.requestPermission(accessory, createUsbPermissionPendingIntent(context));
                    }
                }
            }
        }
    };

    private boolean isValidModel(String accessoryModel) {
        return SKYCONTROLLER2_MODEL_ID.equals(accessoryModel) ||
                SKYCONTROLLER_NG_MODEL_ID.equals(accessoryModel) ||
                SKYCONTROLLER2P_MODEL_ID.equals(accessoryModel);
    }

    public Mux getMux() {
        return usbMux;
    }
}



