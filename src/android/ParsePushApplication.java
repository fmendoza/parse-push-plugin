package github.taivo.parsepushplugin;

import android.app.Application;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.parse.FunctionCallback;
import com.parse.Parse;
import com.parse.ParseCloud;
import com.parse.ParseInstallation;
import com.parse.SaveCallback;
import com.parse.ParseException;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;

/*
   Why is this Application subclass needed?
      - Cordova does not define an Application class, only Activity.
      - The android cold start sequence is: create Application -> ... --> handle push --> ... -> launch Activity,
      - Without configuring an Application class, the app would crash during push notification cold start because
         Parse.Push is not initialized before the "handle push" phase.

   How does Android know to use this subclass as the main application class?
      - In AndroidManifest.xml, the <application> class has an attribute "android:name" that points to your designated main application class.
      - This plugin automatically sets android:name during plugin installation IFF it doesn't exist.
      - If you write your own MainApplication class in your app package, be sure to manually set android:name="MainApplication"
      - If your MainApplication resides in a package other than your main app package, the full path must be specified,
         i.e., android:name="com.custom.package.MainApplication"
*/
public class ParsePushApplication extends Application {
  public static final String LOGTAG = "ParsePushApplication";

  @Override
  public void onCreate() {
    super.onCreate();

    try {
      // Other ways to call ParsePushReaderConfig:
      //
      // - Tell the reader to parse custom parameters, e.g., <preference name="CustomParam1" value="foo" />
      //   ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), null, new String[] {"CustomParam1", "CustomParam2"});
      //
      // - If you write your own MainApplication in your app package, just import com.yourpackage.R and skip detecting R.xml.config
      //   ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), R.xml.config, null);
      //

      // Simple config reading for opensource parse-server:
      // 1st null to detect R.xml.config resource id, 2nd null indicates no custom config param
      //ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), null, null);
      //
      //Parse.initialize(new Parse.Configuration.Builder(this)
      //   .applicationId(config.getAppId())
      //   .server(config.getServerUrl()) // The trailing slash is important, e.g., https://mydomain.com:1337/parse/
      //   .build()
      //);

      //
      // Support parse.com and opensource parse-server
      // 1st null to detect R.xml.config
      ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), null,
          new String[] { "ParseClientKey" });
      if (config.getServerUrl().equalsIgnoreCase("PARSE_DOT_COM")) {
        //
        //initialize for use with legacy parse.com
        Parse.initialize(this, config.getAppId(), config.getClientKey());
      } else {
        Log.d(LOGTAG, "ServerUrl " + config.getServerUrl());
        Log.d(LOGTAG, "NOTE: The trailing slash is important, e.g., https://mydomain.com:1337/parse/");
        Log.d(LOGTAG, "NOTE: Set the clientKey if your server requires it, otherwise it can be null");
        //
        // initialize for use with opensource parse-server
        Parse.initialize(new Parse.Configuration.Builder(this).applicationId(config.getAppId())
            .server(config.getServerUrl()).clientKey(config.getClientKey()).build());
      }

      Log.d(LOGTAG, "Saving Installation in background");
      //
      // save installation. Parse.Push will need this to push to the correct device
      ParseInstallation.getCurrentInstallation().saveInBackground(new SaveCallback() {
        @Override
        public void done(ParseException ex) {
          if (ex == null) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
              if (ParseInstallation.getCurrentInstallation().get("deviceToken") == null) {
                logDeviceToken(config.getGcmSenderId());
              }
            }
        }
        }
      });

    } catch (ParsePushConfigException ex) {
      Log.e(LOGTAG, ex.toString());
    }
  }

  private void logDeviceToken(String senderId) {
    AsyncTask.execute(new Runnable() {
      @Override
      public void run() {
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(getApplicationContext());
        try {
          String token = gcm.register(senderId);
          setDeviceTokenOnInstallation(token);
        } catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    });
  }

  private void setDeviceTokenOnInstallation(String token) {

    HashMap<String, Object> params = new HashMap<>();
    params.put("id", ParseInstallation.getCurrentInstallation().getObjectId());
    params.put("token", token);
    ParseCloud.callFunctionInBackground("saveDeviceToken", params, new FunctionCallback<ParseInstallation>() {
      public void done(final ParseInstallation success, ParseException e) {
        if (e == null) {
          Log.v("1020", "SETTING DEVICE TOKEN OK");
        }
      }
    });
  }

}
