package github.taivo.parsepushplugin;

import android.app.Application;

import com.parse.Parse;
import com.parse.Parse.Configuration.Builder;
import com.parse.ParseInstallation;
import com.parse.SaveCallback;
import com.parse.ParseException;

import github.taivo.parsepushplugin.ParsePushConfigReader;
import github.taivo.parsepushplugin.ParsePushConfigException;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.parse.fcm.ParseFCM;

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

      // Simple config reading for opensource parse-server:
      // 1st null to detect R.xml.config resource id, 2nd null indicates no custom config param
      //ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), null, null);

      ParsePushConfigReader config = new ParsePushConfigReader(
        getApplicationContext(), null, new String[] { "ParseClientKey" }
      );
     
      Log.d(LOGTAG, "ServerUrl " + config.getServerUrl());
      Log.d(LOGTAG, "NOTE: The trailing slash is important, e.g., https://mydomain.com:1337/parse/");
        
      Parse.initialize(new Parse.Configuration.Builder(this)
      .applicationId(config.getAppId())
      .server(config.getServerUrl())
      .build());

      Log.d(LOGTAG, "Saving Installation in background");

      ParseInstallation.getCurrentInstallation().saveInBackground(new SaveCallback() {
        @Override
        public void done(ParseException ex) {
          if (null != ex) {
            Log.e(LOGTAG, ex.toString());
          } else {
            Log.d(LOGTAG, "Installation saved");

            String deviceToken = ParseInstallation.getCurrentInstallation().getString("deviceToken");

            if (deviceToken == null) {
              FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                  @Override
                  public void onComplete(@NonNull Task<InstanceIdResult> task) {

                    if (!task.isSuccessful()) {
                      return;
                    }

                    // Get new Instance ID token
                    String token = task.getResult().getToken();
                    ParseFCM.register(token);
                  }
                });
            }

          }
        }
      });

    } catch (ParsePushConfigException ex) {
      Log.e(LOGTAG, ex.toString());
    }
  }
}
