/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.permission.cts.appthataccesseslocation;

import android.app.Service;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Timer;
import java.util.TimerTask;

public class AccessLocationOnCommand extends Service implements LocationListener {
    private static final String TEST_PROVIDER = "test_provider";

    private LocationManager mLocationManager;
    private IAccessLocationOnCommand.Stub mBinder;
    private final LocationListener mLocationListener = this;
    private final Timer mTimer = new Timer();

    private void updateMockLocation() {
        final Location location = new Location(TEST_PROVIDER);
        location.setLatitude(35.657f);
        location.setLongitude(139.703f);
        location.setAccuracy(1.0f);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        mLocationManager.setTestProviderLocation(TEST_PROVIDER, location);
    }

    @Override
    public IBinder onBind(Intent intent) {
        mLocationManager = getSystemService(LocationManager.class);

        mLocationManager.addTestProvider(TEST_PROVIDER,
                /* requiresNetwork= */true,
                /* requiresSatellite= */false,
                /* requiresCell= */true,
                /* hasMonetaryCost= */false,
                /* supportsAltitude= */false,
                /* supportsSpeed= */false,
                /* supportsBearing= */false,
                Criteria.POWER_HIGH,
                Criteria.ACCURACY_FINE);
        mLocationManager.setTestProviderEnabled(TEST_PROVIDER, true);

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateMockLocation();
            }
        }, 0, 1000);

        mBinder = new IAccessLocationOnCommand.Stub() {
            public void accessLocation() {
                mLocationManager.requestSingleUpdate(
                        TEST_PROVIDER, mLocationListener, Looper.getMainLooper());
            }
        };
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mTimer.cancel();
        mLocationManager.removeTestProvider(TEST_PROVIDER);
        return true;
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onStatusChanged(String provider, int status,
            Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

}
