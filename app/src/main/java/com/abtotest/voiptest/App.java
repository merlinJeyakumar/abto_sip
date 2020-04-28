package com.abtotest.voiptest;
import android.content.IntentFilter;

import org.abtollc.sdk.AbtoApplication;
import org.abtollc.sdk.AbtoPhone;

public class App extends AbtoApplication {

    private static App app;

    private CallEventsReceiver callEventsReceiver = new CallEventsReceiver();
    private AppInBackgroundHandler appInBackgroundHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        registerReceiver(callEventsReceiver, new IntentFilter(AbtoPhone.ACTION_ABTO_CALL_EVENT));

        appInBackgroundHandler = new AppInBackgroundHandler();
        registerActivityLifecycleCallbacks(appInBackgroundHandler);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(callEventsReceiver);
        unregisterActivityLifecycleCallbacks(appInBackgroundHandler);
    }

    public static App getApp() {
        return app;
    }

    public boolean isAppInBackground() {
        return appInBackgroundHandler.isAppInBackground();
    }
}
