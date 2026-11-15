# jaudiotagger references desktop Java classes (AWT/ImageIO/Swing) that do not exist on
# Android. The code paths that use them are never reached on Android.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.xml.bind.**
-dontwarn org.jaudiotagger.**
-keep class org.jaudiotagger.** { *; }

# Room entities / DAOs are generated and kept by Room's own rules.
-keep class com.ultramusicplayer.app.data.db.** { *; }

# Widget providers and service are referenced from the manifest (kept automatically),
# keep the EQ processor in case of reflective Media3 lookups.
-keep class com.ultramusicplayer.app.playback.** { *; }
