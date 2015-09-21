/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.vehiclenetwork;

import android.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.car.vehiclenetwork.VehicleNetworkConsts.VehicleValueType;
import com.android.car.vehiclenetwork.VehicleNetworkProto.VehiclePropConfigs;
import com.android.car.vehiclenetwork.VehicleNetworkProto.VehiclePropValue;
import com.android.car.vehiclenetwork.VehicleNetworkProto.VehiclePropValues;

import java.lang.ref.WeakReference;

/**
 * System API to access Vehicle network. This is only for system services and applications should
 * not use this. All APIs will fail with security error if normal app tries this.
 */
public class VehicleNetwork {
    public interface VehicleNetworkListener {
        void onVehicleNetworkEvents(VehiclePropValues values);
    }

    private static final String TAG = VehicleNetwork.class.getSimpleName();

    private final IVehicleNetwork mService;
    private final VehicleNetworkListener mListener;
    private final IVehicleNetworkListenerImpl mVehicleNetworkListener;
    private final EventHandler mEventHandler;

    public static VehicleNetwork createVehicleNetwork(VehicleNetworkListener listener,
            Looper looper) {
        IVehicleNetwork service = IVehicleNetwork.Stub.asInterface(ServiceManager.getService(
                IVehicleNetwork.class.getCanonicalName()));
        if (service == null) {
            throw new RuntimeException("Vehicle network service not available:" +
                    IVehicleNetwork.class.getCanonicalName());
        }
        return new VehicleNetwork(service, listener, looper);
    }

    private VehicleNetwork(IVehicleNetwork service, VehicleNetworkListener listener,
            Looper looper) {
        mService = service;
        mListener = listener;
        mEventHandler = new EventHandler(looper);
        mVehicleNetworkListener = new IVehicleNetworkListenerImpl(this);
    }

    public VehiclePropConfigs listProperties() {
        return listProperties(0 /* all */);
    }

    public VehiclePropConfigs listProperties(int property) {
        try {
            VehiclePropConfigsParcelable parcelable = mService.listProperties(property);
            if (parcelable != null) {
                return parcelable.configs;
            }
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
        return null;
    }

    public void setProperty(VehiclePropValue value) {
        VehiclePropValueParcelable parcelable = new VehiclePropValueParcelable(value);
        try {
            mService.setProperty(parcelable);
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
    }

    public void setIntProperty(int property, int value) {
        VehiclePropValue v = VehiclePropValue.newBuilder().
                setProp(property).
                setValueType(VehicleValueType.VEHICLE_VALUE_TYPE_INT32).
                addInt32Values(value).
                build();
        setProperty(v);
    }

    public void setIntVectorProperty(int property, int[] values) {
        int len = values.length;
        VehiclePropValue.Builder builder = VehiclePropValue.newBuilder().
                setProp(property).
                setValueType(VehicleValueType.VEHICLE_VALUE_TYPE_INT32_VEC2 + len - 2);
        for (int v : values) {
            builder.addInt32Values(v);
        }
        setProperty(builder.build());
    }

    public void setLongProperty(int property, long value) {
        VehiclePropValue v = VehiclePropValue.newBuilder().
                setProp(property).
                setValueType(VehicleValueType.VEHICLE_VALUE_TYPE_INT64).
                setInt64Value(value).
                build();
        setProperty(v);
    }

    public void setFloatProperty(int property, float value) {
        VehiclePropValue v = VehiclePropValue.newBuilder().
                setProp(property).
                setValueType(VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT).
                addFloatValues(value).
                build();
        setProperty(v);
    }

    public void setFloatVectorProperty(int property, float[] values) {
        int len = values.length;
        VehiclePropValue.Builder builder = VehiclePropValue.newBuilder().
                setProp(property).
                setValueType(VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT_VEC2 + len - 2);
        for (float v : values) {
            builder.addFloatValues(v);
        }
        setProperty(builder.build());
    }

    public VehiclePropValue getProperty(int property) {
        try {
            VehiclePropValueParcelable parcelable = mService.getProperty(property);
            if (parcelable != null) {
                return parcelable.value;
            }
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
        return null;
    }

    public int getIntProperty(int property) {
        VehiclePropValue v = getProperty(property);
        if (v == null) {
            // if property is invalid, IllegalArgumentException should have been thrown
            // from getProperty.
            throw new IllegalStateException();
        }
        if (v.getValueType() != VehicleValueType.VEHICLE_VALUE_TYPE_INT32) {
            throw new IllegalArgumentException();
        }
        if (v.getInt32ValuesCount() != 1) {
            throw new IllegalStateException();
        }
        return v.getInt32Values(0);
    }

    public void getIntVectorProperty(int property, int[] values) {
        VehiclePropValue v = getProperty(property);
        if (v == null) {
            // if property is invalid, IllegalArgumentException should have been thrown
            // from getProperty.
            throw new IllegalStateException();
        }
        switch (v.getValueType()) {
            case VehicleValueType.VEHICLE_VALUE_TYPE_INT32_VEC2:
            case VehicleValueType.VEHICLE_VALUE_TYPE_INT32_VEC3:
            case VehicleValueType.VEHICLE_VALUE_TYPE_INT32_VEC4:
                if (values.length !=
                    (v.getValueType() - VehicleValueType.VEHICLE_VALUE_TYPE_INT32_VEC2 + 2)) {
                    throw new IllegalArgumentException("wrong array length");
                }
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (v.getInt32ValuesCount() != values.length) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = v.getInt32Values(i);
        }
    }

    public float getFloatProperty(int property) {
        VehiclePropValue v = getProperty(property);
        if (v == null) {
            throw new IllegalStateException();
        }
        if (v.getValueType() != VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT) {
            throw new IllegalArgumentException();
        }
        if (v.getFloatValuesCount() != 1) {
            throw new IllegalStateException();
        }
        return v.getFloatValues(0);
    }

    public void getFloatVectorProperty(int property, float[] values) {
        VehiclePropValue v = getProperty(property);
        if (v == null) {
            // if property is invalid, IllegalArgumentException should have been thrown
            // from getProperty.
            throw new IllegalStateException();
        }
        switch (v.getValueType()) {
            case VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT_VEC2:
            case VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT_VEC3:
            case VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT_VEC4:
                if (values.length !=
                    (v.getValueType() - VehicleValueType.VEHICLE_VALUE_TYPE_FLOAT_VEC2 + 2)) {
                    throw new IllegalArgumentException("wrong array length");
                }
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (v.getFloatValuesCount() != values.length) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = v.getFloatValues(i);
        }
    }

    public long getLongProperty(int property) {
        VehiclePropValue v = getProperty(property);
        if (v == null) {
            throw new IllegalStateException();
        }
        if (v.getValueType() != VehicleValueType.VEHICLE_VALUE_TYPE_INT64) {
            throw new IllegalArgumentException();
        }
        return v.getInt64Value();
    }

    //TODO check UTF8 to java string conversion
    public String getStringProperty(int property) {
        VehiclePropValue v = getProperty(property);
        if (v == null) {
            throw new IllegalStateException();
        }
        if (v.getValueType() != VehicleValueType.VEHICLE_VALUE_TYPE_STRING) {
            throw new IllegalArgumentException();
        }
        return v.getStringValue();
    }

    public void subscribe(int property, float sampleRate) {
        try {
            mService.subscribe(mVehicleNetworkListener, property, sampleRate);
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
    }

    public void unsubscribe(int property) {
        try {
            mService.unsubscribe(mVehicleNetworkListener, property);
        } catch (RemoteException e) {
            handleRemoteException(e);
        }
    }

    private void handleRemoteException(RemoteException e) {
        throw new RuntimeException("Vehicle network service not working ", e);
    }

    private void handleVehicleNetworkEvents(VehiclePropValues values) {
        mEventHandler.notifyEvents(values);
    }

    private void doHandleVehicleNetworkEvents(VehiclePropValues values) {
        mListener.onVehicleNetworkEvents(values);
    }

    private class EventHandler extends Handler {
        private static final int MSG_EVENTS = 0;

        private EventHandler(Looper looper) {
            super(looper);
        }

        private void notifyEvents(VehiclePropValues values) {
            Message msg = obtainMessage(MSG_EVENTS, values);
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_EVENTS:
                    doHandleVehicleNetworkEvents((VehiclePropValues)msg.obj);
                    break;
                default:
                    Log.w(TAG, "unown message:" + msg.what, new RuntimeException());
                    break;
            }
        }
    }

    private static class IVehicleNetworkListenerImpl extends IVehicleNetworkListener.Stub {
        private final WeakReference<VehicleNetwork> mVehicleNetwork;

        private IVehicleNetworkListenerImpl(VehicleNetwork vehicleNewotk) {
            mVehicleNetwork = new WeakReference<VehicleNetwork>(vehicleNewotk);
        }

        @Override
        public void onVehicleNetworkEvents(VehiclePropValuesParcelable values) {
            VehicleNetwork vehicleNetwork = mVehicleNetwork.get();
            if (vehicleNetwork != null) {
                vehicleNetwork.handleVehicleNetworkEvents(values.values);
            }
        }
    }
}