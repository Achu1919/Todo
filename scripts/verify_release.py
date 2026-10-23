#!/usr/bin/env python3
"""v0.8.1 release-dex check: every class the platform or a library creates *by name* must survive
R8 unobfuscated and keep the constructor it is created with.

  python3 scripts/verify_release.py [path/to/app-release.apk] [path/to/mapping.txt]

Checks (exit 1 on any failure):
  * every manifest activity / service / receiver / provider and androidx.startup initializer
    exists under its own name with a public no-arg constructor;
  * every class extending androidx.work.InputMerger and implementing Glance's ActionCallback has a
    no-arg constructor (instantiated by name: WorkSpec.inputMergerClassName, actionRunCallback<T>);
  * every ListenableWorker subclass keeps (Context, WorkerParameters);
  * Room's generated *_Impl databases keep their no-arg constructor.
"""
import glob, os, re, subprocess, sys, tempfile, zipfile

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
apk = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "app/build/outputs/apk/release/app-release.apk")
mapping = sys.argv[2] if len(sys.argv) > 2 else os.path.join(root, "app/build/outputs/mapping/release/mapping.txt")
sdk = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk"))
bt = sorted(glob.glob(os.path.join(sdk, "build-tools", "*")))[-1]

# original -> obfuscated class names
obf = {}
for line in open(mapping, encoding="utf-8"):
    m = re.match(r"^(\S+) -> (\S+):$", line)
    if m:
        obf[m.group(1)] = m.group(2)
deobf = {v: k for k, v in obf.items()}

classes = {}  # dex name -> (super, [interfaces], [ctor sigs])
with tempfile.TemporaryDirectory() as tmp:
    with zipfile.ZipFile(apk) as z:
        dexes = [n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
        for n in dexes:
            z.extract(n, tmp)
    for n in dexes:
        out = subprocess.run([os.path.join(bt, "dexdump"), os.path.join(tmp, n)], capture_output=True).stdout.decode("latin-1")
        for block in re.split(r"\n(?=Class #\d+)", out):
            m = re.search(r"Class descriptor  : 'L([^;]+);'", block)
            if not m:
                continue
            name = m.group(1).replace("/", ".")
            sup = re.search(r"Superclass        : 'L([^;]+);'", block)
            ifaces = [i.replace("/", ".") for i in re.findall(r"#\d+\s+: 'L([^;]+);'\n", block.split("Static fields")[0])]
            ctors = re.findall(r"name          : '<init>'\n\s+type          : '([^']+)'", block)
            classes[name] = (sup.group(1).replace("/", ".") if sup else None, ifaces, ctors)

def orig(n): return deobf.get(n, n)
def dexname(n): return obf.get(n, n)

def ancestors(n):
    seen, todo = set(), [n]
    while todo:
        c = todo.pop()
        if c in seen or c not in classes:
            continue
        seen.add(c)
        sup, ifs, _ = classes[c]
        todo += ([sup] if sup else []) + ifs
    return {orig(c) for c in seen}

fails, checked = [], 0
def need(name, sig, why):
    global checked
    checked += 1
    c = classes.get(name)
    if c is None:
        fails.append(f"MISSING  {name} ({why})" + (f" -> renamed to {obf[name]}" if name in obf and obf[name] != name else ""))
    elif sig not in c[2]:
        fails.append(f"NO CTOR  {name}{sig} ({why}); has {c[2]}")

# 1. manifest components + startup initializers
tree = subprocess.run([os.path.join(bt, "aapt2"), "dump", "xmltree", apk, "--file", "AndroidManifest.xml"], capture_output=True, text=True).stdout
pkg = re.search(r'package="([^"]+)"', tree).group(1)
elem = None
for line in tree.splitlines():
    e = re.match(r"\s*E: (\S+)", line)
    if e:
        elem = e.group(1); continue
    a = re.match(r'\s*A: http://schemas.android.com/apk/res/android:name\(0x01010003\)="([^"]+)"', line)
    if a and elem in ("activity", "activity-alias", "service", "receiver", "provider", "application"):
        n = a.group(1); n = pkg + n if n.startswith(".") else n
        if elem != "activity-alias":
            need(n, "()V", f"manifest <{elem}>")
    if a and elem == "meta-data" and 'value' not in line:
        pending = a.group(1)
    v = re.match(r'\s*A: http://schemas.android.com/apk/res/android:value\(0x01010024\)="androidx.startup"', line)
    if v and elem == "meta-data":
        need(pending, "()V", "androidx.startup initializer")

# 2. by-name library contracts, found by (deobfuscated) ancestry
for n, (sup, ifs, ctors) in classes.items():
    anc = ancestors(n)
    o = orig(n)
    if "androidx.work.InputMerger" in anc and o != "androidx.work.InputMerger":
        if o != n: fails.append(f"RENAMED  {o} -> {n} (InputMerger, named in WorkSpecs)")
        need(n, "()V", "InputMerger")
    if "androidx.glance.appwidget.action.ActionCallback" in anc and o != "androidx.glance.appwidget.action.ActionCallback":
        if o != n: fails.append(f"RENAMED  {o} -> {n} (ActionCallback, named in PendingIntents)")
        need(n, "()V", "Glance ActionCallback")
    if "androidx.work.ListenableWorker" in anc and o not in ("androidx.work.ListenableWorker",) and not o.startswith("androidx.work.CoroutineWorker") and o != "androidx.work.Worker":
        if o != n: fails.append(f"RENAMED  {o} -> {n} (Worker, named in WorkSpecs)")
        need(n, "(Landroid/content/Context;Landroidx/work/WorkerParameters;)V", "ListenableWorker")
    if o.endswith("_Impl") and "androidx.room.RoomDatabase" in anc:
        need(n, "()V", "Room database")

# 3. explicit must-haves for the Today widget (v0.8.1)
need("androidx.work.OverwritingInputMerger", "()V", "WorkManager default InputMerger")
need("dev.saikrishna.todo.widget.CompleteTaskAction", "()V", "actionRunCallback<CompleteTaskAction>")
need("dev.saikrishna.todo.widget.TodayWidgetReceiver", "()V", "widget receiver")
need("dev.saikrishna.todo.widget.TodayWidget", "()V", "widget (name stored by GlanceAppWidgetManager)")
need("androidx.glance.session.SessionWorker", "(Landroid/content/Context;Landroidx/work/WorkerParameters;)V", "Glance session")

# 4. v0.12 add-task widgets: receivers + widget classes, and each receiver is a real app widget
#    provider (APPWIDGET_UPDATE filter, provider xml with a preview layout + image) in the APK.
Widgets = {"TodayWidget": "today_widget_info", "AddPillWidget": "add_pill_widget_info", "AddRoundWidget": "add_round_widget_info",
           "QuickListsWidget": "quick_lists_widget_info", "HubWidget": "hub_widget_info"}
for w in Widgets:
    need(f"dev.saikrishna.todo.widget.{w}Receiver", "()V", "widget receiver")
    need(f"dev.saikrishna.todo.widget.{w}", "()V", "widget (name stored by GlanceAppWidgetManager)")
need("dev.saikrishna.todo.ui.capture.CaptureActivity", "()V", "widget capture target")
blocks = re.split(r"\n\s*E: receiver", tree)
for w, info in Widgets.items():
    checked += 1
    b = next((b for b in blocks if f'"{pkg}.widget.{w}Receiver"' in b or f'".widget.{w}Receiver"' in b), None)
    if b is None:
        fails.append(f"NO RECEIVER  {w}Receiver in manifest"); continue
    b = re.split(r"\n\s*E: (?:receiver|service|activity|provider)\b", b)[0]
    if "android.appwidget.action.APPWIDGET_UPDATE" not in b or "android.appwidget.provider" not in b:
        fails.append(f"NOT A WIDGET  {w}Receiver (APPWIDGET_UPDATE / provider meta-data)")
    if 'exported(0x01010010)=(type 0x12)0xffffffff' not in b:
        fails.append(f"NOT EXPORTED  {w}Receiver")
    res = subprocess.run([os.path.join(bt, "aapt2"), "dump", "resources", apk], capture_output=True, text=True).stdout
    m = re.search(rf"resource 0x[0-9a-f]+ xml/{info}\n\s+\(\) \(file\) (\S+)", res)
    if not m:
        fails.append(f"NO PROVIDER XML  xml/{info}"); continue
    px = subprocess.run([os.path.join(bt, "aapt2"), "dump", "xmltree", apk, "--file", m.group(1)], capture_output=True, text=True).stdout
    if w != "TodayWidget" and ("previewLayout" not in px or "previewImage" not in px):
        fails.append(f"NO PREVIEW  xml/{info} (previewLayout + previewImage)")
    if "initialLayout" not in px:
        fails.append(f"NO INITIAL LAYOUT  xml/{info}")

print(f"{os.path.basename(apk)}: {len(classes)} classes, {checked} by-name checks")
for f in fails:
    print("  " + f)
print("OK" if not fails else f"FAILED ({len(fails)})")
sys.exit(1 if fails else 0)
