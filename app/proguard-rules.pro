# ---- kotlinx.serialization (مدل‌های گزارش و تنظیمات که به JSON ذخیره می‌شوند) ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ir.pingscore.app.**$$serializer { *; }
-keepclassmembers class ir.pingscore.app.** {
    *** Companion;
}
-keepclasseswithmembers class ir.pingscore.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- OkHttp (کتابخانه‌های اختیاری که ممکن است وجود نداشته باشند) ----
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- WorkManager: Worker با reflection ساخته می‌شود ----
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---- Room (قوانین اصلی را خود Room اضافه می‌کند) ----
-keep class * extends androidx.room.RoomDatabase
