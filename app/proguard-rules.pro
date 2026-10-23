# Todo release keep rules (v0.8.1).
#
# AGP 8+ runs R8 in full mode: "-keep class X" without a member list keeps the class name but NOT
# its no-arg constructor. Everything below is instantiated by class name at runtime, so its
# constructor must survive. Verified against the release dex by scripts/verify_release.py.

# --- WorkManager (Glance renders widgets in androidx.glance.session.SessionWorker) -------------
# WorkerWrapper instantiates the InputMerger named in every WorkSpec (default
# androidx.work.OverwritingInputMerger) with Class.newInstance(); without the constructor the
# work fails before the worker runs, so the widget session never starts (eternal spinner).
-keep class * extends androidx.work.InputMerger { public <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.WorkerParameters
-keep class androidx.work.impl.WorkDatabase_Impl { public <init>(); }
# Room creates <Database>_Impl by reflection (ours and WorkManager's).
-keep class * extends androidx.room.RoomDatabase { public <init>(); }
# androidx.startup creates Initializers (WorkManagerInitializer, ...) named in the manifest.
-keep class * implements androidx.startup.Initializer { public <init>(); }

# --- Glance ---------------------------------------------------------------------------------------
# actionRunCallback<T>() stores T's class name; ActionCallbackBroadcastReceiver creates it with
# the no-arg constructor. Glance's own rule keeps only the class.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { public <init>(); }
# Our widget: the receiver is created by the system; the widget class name is stored by
# GlanceAppWidgetManager (receiver -> provider), so keep it stable across app updates.
-keep class dev.saikrishna.todo.widget.TodayWidgetReceiver { public <init>(); }
-keep class dev.saikrishna.todo.widget.TodayWidget { public <init>(); }
# v0.12 add-task widgets: same contract as the Today widget (receivers created by the system,
# widget class names stored by GlanceAppWidgetManager).
-keep class dev.saikrishna.todo.widget.*WidgetReceiver { public <init>(); }
-keep class dev.saikrishna.todo.widget.AddPillWidget { public <init>(); }
-keep class dev.saikrishna.todo.widget.AddRoundWidget { public <init>(); }
-keep class dev.saikrishna.todo.widget.QuickListsWidget { public <init>(); }
-keep class dev.saikrishna.todo.widget.HubWidget { public <init>(); }
