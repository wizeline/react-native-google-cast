package com.googlecast;

import android.content.Context;
import android.util.Log;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.google.android.gms.cast.framework.CastContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GoogleCastPackage implements ReactPackage {
    private boolean mCastingSupported = true;

    public GoogleCastPackage(Context context) {
      try {
          CastContext castContext = CastContext.getSharedInstance(context);
      } catch (Exception ex) {
          // Casting failed probably because of play services
          Log.i("GoogleCastPackage", "Failed to start cast context");
          mCastingSupported = false;
      }
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();

        modules.add(new GoogleCastModule(reactContext, mCastingSupported));

        return modules;
    }

    public List<Class<? extends JavaScriptModule>> createJSModules() {
        return Collections.emptyList();
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        List<ViewManager> managers = new ArrayList<>();

        managers.add(new GoogleCastButtonManager());

        return managers;
    }
}
