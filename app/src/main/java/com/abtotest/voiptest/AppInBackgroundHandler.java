package com.abtotest.voiptest;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class AppInBackgroundHandler implements Application.ActivityLifecycleCallbacks {

    private int activeActivities = 0;

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) { }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {  }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        activeActivities++;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        activeActivities--;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) { }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) { }

    public boolean isAppInBackground() { return activeActivities == 0; }
}