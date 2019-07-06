//
// Note: use before_plugin_install hook instead of after_plugin_install.
// Cordova generates its own version of AndroidManifest and saves it after the after_plugin_install hook
// so our updates would be overwritten. This is true for Cordova 5.4.1.
//
var DefaultApplicationName = ["github.taivo.parsepushplugin", "ParsePushApplication"].join('.');

module.exports = function(context) {
   var path = require('path');
   var ConfigFile = context.requireCordovaModule("cordova-common").ConfigFile;

   var androidPrjDir = path.join(context.opts.projectRoot, 'platforms/android/app/src/main');
   var androidManifest = new ConfigFile(androidPrjDir, 'android', 'AndroidManifest.xml');

   //
   // because the user may have customized <application> android:name, remove it IFF it is the default name
   //
   var applicationNode = androidManifest.data.find('application');
   if(applicationNode.get('android:name') === DefaultApplicationName){
      delete applicationNode.attrib['android:name'];
      androidManifest.is_changed = true;
   }

   if(androidManifest.is_changed){
      androidManifest.save();
   }

   return true;
}
