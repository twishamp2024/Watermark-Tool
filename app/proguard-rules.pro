# kotlinx.serialization ships its own consumer rules; nothing app-specific is reflected on.
# WorkManager workers are instantiated by class name — keep their constructors.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
