# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-dontwarn javax.annotation.**

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }
