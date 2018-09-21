package com.googlecast;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class GoogleCastModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    @VisibleForTesting
    public static final String REACT_CLASS = "RNGoogleCast";

    protected static final String SESSION_STARTING = "GoogleCast:SessionStarting";
    protected static final String SESSION_STARTED = "GoogleCast:SessionStarted";
    protected static final String SESSION_START_FAILED = "GoogleCast:SessionStartFailed";
    protected static final String SESSION_SUSPENDED = "GoogleCast:SessionSuspended";
    protected static final String SESSION_RESUMING = "GoogleCast:SessionResuming";
    protected static final String SESSION_RESUMED = "GoogleCast:SessionResumed";
    protected static final String SESSION_ENDING = "GoogleCast:SessionEnding";
    protected static final String SESSION_ENDED = "GoogleCast:SessionEnded";

    protected static final String STATE_CHANGED = "GoogleCast:StateChanged";

    protected static final String MEDIA_STATUS_UPDATED = "GoogleCast:MediaStatusUpdated";
    protected static final String MEDIA_PLAYBACK_STARTED = "GoogleCast:MediaPlaybackStarted";
    protected static final String MEDIA_PLAYBACK_ENDED = "GoogleCast:MediaPlaybackEnded";

    private static final String TAG = "GoogleCastModule";

    private CastSession mCastSession;
    private CastStateListener mCastStateListener;
    private SessionManagerListener<CastSession> mSessionManagerListener;

    private boolean mCastingSupported = true;

    public GoogleCastModule(ReactApplicationContext reactContext, boolean castingSupported) {
        super(reactContext);

        mCastingSupported = castingSupported;

        if (mCastingSupported) {
            reactContext.addLifecycleEventListener(this);
            setupCastListener();
        }
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();

        constants.put("CASTING_SUPPORTED", mCastingSupported);

        constants.put("SESSION_STARTING", SESSION_STARTING);
        constants.put("SESSION_STARTED", SESSION_STARTED);
        constants.put("SESSION_START_FAILED", SESSION_START_FAILED);
        constants.put("SESSION_SUSPENDED", SESSION_SUSPENDED);
        constants.put("SESSION_RESUMING", SESSION_RESUMING);
        constants.put("SESSION_RESUMED", SESSION_RESUMED);
        constants.put("SESSION_ENDING", SESSION_ENDING);
        constants.put("SESSION_ENDED", SESSION_ENDED);

        constants.put("STATE_CHANGED", STATE_CHANGED);

        constants.put("MEDIA_STATUS_UPDATED", MEDIA_STATUS_UPDATED);
        constants.put("MEDIA_PLAYBACK_STARTED", MEDIA_PLAYBACK_STARTED);
        constants.put("MEDIA_PLAYBACK_ENDED", MEDIA_PLAYBACK_ENDED);

        return constants;
    }

    protected void emitMessageToRN(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName,
                params);
    }

    @ReactMethod
    public void castMedia(final ReadableMap params) {
        if (mCastSession == null) {
            return;
        }

        getReactApplicationContext().runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
                if (remoteMediaClient == null) {
                    Log.e(TAG, "Failed to cast, no client");
                    return;
                }

                Integer position = null;
                if (params.hasKey("position")) {
                    position = params.getInt("position");
                }
                if (position == null) {
                    position = 0;
                }

                remoteMediaClient.load(buildMediaInfo(params), true, position);

                Log.e(REACT_CLASS, "Casting media... ");
            }
        });
    }

    private MediaInfo buildMediaInfo(ReadableMap params) {
        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

        if (params.hasKey("title") && params.getString("title") != null) {
            movieMetadata.putString(MediaMetadata.KEY_TITLE, params.getString("title"));
        }

        if (params.hasKey("subtitle") && params.getString("subtitle") != null) {
            movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, params.getString("subtitle"));
        }

        if (params.hasKey("posterUrl") && params.getString("posterUrl") != null) {
            movieMetadata.addImage(new WebImage(Uri.parse(params.getString("posterUrl"))));
        }

        String contentType = "video/mp4";
        if (params.hasKey("contentType") && params.getString("contentType") != null) {
            contentType = params.getString("contentType");
        }

        MediaInfo.Builder builder = new MediaInfo.Builder(params.getString("mediaUrl"))
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(contentType).setMetadata(movieMetadata);

        if (params.hasKey("customData")) {
            ReadableMap customData = params.getMap("customData");
            JSONObject json = null;
            try {
                json = new JSONObject(customData.toHashMap());
            } catch (Exception e) {
                Log.e(null, "Unable to convert custom data to json, must be strings only", e);
            }

            builder.setCustomData(json);
        }

        if (params.hasKey("duration")) {
            builder = builder.setStreamDuration(params.getInt("duration"));
        }

        return builder.build();
    }

    @ReactMethod
    public void getCastState(final Promise promise) {
        getReactApplicationContext().runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                if (!mCastingSupported) {
                    promise.resolve(CastState.NOT_CONNECTED);
                } else {
                    CastContext castContext = CastContext.getSharedInstance(getReactApplicationContext());
                    promise.resolve(castContext.getCastState());
                }
            }
        });
    }

    @ReactMethod
    public void play() {
        if (mCastSession != null) {
            getReactApplicationContext().runOnUiQueueThread(new Runnable() {
                @Override
                public void run() {
                    RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
                    if (remoteMediaClient == null) {
                        Log.e(TAG, "Failed to play, no client");
                        return;
                    }

                    remoteMediaClient.play();
                }
            });
        }
    }

    @ReactMethod
    public void pause() {
        if (mCastSession != null) {
            getReactApplicationContext().runOnUiQueueThread(new Runnable() {
                @Override
                public void run() {
                    RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
                    if (remoteMediaClient == null) {
                        Log.e(TAG, "Failed to pause, no client");
                        return;
                    }

                    remoteMediaClient.pause();
                }
            });
        }
    }

    @ReactMethod
    public void stop() {
        if (mCastSession != null) {
            getReactApplicationContext().runOnUiQueueThread(new Runnable() {
                @Override
                public void run() {
                    RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
                    if (remoteMediaClient == null) {
                        Log.e(TAG, "Failed to stop, no client");
                        return;
                    }

                    remoteMediaClient.stop();
                }
            });
        }
    }

    @ReactMethod
    public void seek(final int position) {
        if (mCastSession != null) {
            getReactApplicationContext().runOnUiQueueThread(new Runnable() {
                @Override
                public void run() {
                    RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
                    if (remoteMediaClient == null) {
                        Log.e(TAG, "Failed to seek, no client");
                        return;
                    }

                    remoteMediaClient.seek(position * 1000);
                }
            });
        }
    }

    @ReactMethod
    public void endSession(final boolean stopCasting, final Promise promise) {
        getReactApplicationContext().runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                SessionManager sessionManager = CastContext.getSharedInstance(getReactApplicationContext())
                        .getSessionManager();
                sessionManager.endCurrentSession(stopCasting);
                promise.resolve(true);
            }
        });
    }

    @ReactMethod
    public void launchExpandedControls() {
        ReactApplicationContext context = getReactApplicationContext();
        Intent intent = new Intent(context, GoogleCastExpandedControlsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void setupCastListener() {
        mSessionManagerListener = new GoogleCastSessionManagerListener(this);

        mCastStateListener = new CastStateListener() {
            @Override
            public void onCastStateChanged(int newState) {
                WritableMap event = Arguments.createMap();
                event.putInt("state", newState);
                emitMessageToRN(GoogleCastModule.STATE_CHANGED, event);
            }
        };
    }

    @Override
    public void onHostResume() {
        getReactApplicationContext().runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                CastContext castContext = CastContext.getSharedInstance(getReactApplicationContext());
                castContext.addCastStateListener(mCastStateListener);

                SessionManager sessionManager = castContext.getSessionManager();
                sessionManager.addSessionManagerListener(mSessionManagerListener, CastSession.class);
            }
        });
    }

    @Override
    public void onHostPause() {
        getReactApplicationContext().runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                CastContext castContext = CastContext.getSharedInstance(getReactApplicationContext());
                castContext.removeCastStateListener(mCastStateListener);

                SessionManager sessionManager = castContext.getSessionManager();
                sessionManager.removeSessionManagerListener(mSessionManagerListener, CastSession.class);
            }
        });
    }

    @Override
    public void onHostDestroy() {
    }

    protected void setCastSession(CastSession castSession) {
        this.mCastSession = castSession;
    }

    protected CastSession getCastSession() {
        return mCastSession;
    }

    protected void runOnUiQueueThread(Runnable runnable) {
        getReactApplicationContext().runOnUiQueueThread(runnable);
    }
}
