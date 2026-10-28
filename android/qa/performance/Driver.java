package com.hector.workoutbenchmark;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.content.Intent;
import android.graphics.Rect;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import org.json.*;

/** Runs in its own process, so the real release app can be stopped and measured without test code. */
public class Driver extends Instrumentation {
    private UiAutomation ui;
    private Bundle args;
    private final String app = "com.hector.workouttracker";
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = ui.executeShellCommand(command); InputStream in = new FileInputStream(fd.getFileDescriptor())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }
    private String text(AccessibilityNodeInfo n) {
        return String.valueOf(n.getText()) + " " + String.valueOf(n.getContentDescription());
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo n, Predicate<AccessibilityNodeInfo> p) {
        if (n == null) return null;
        Rect r = new Rect(); n.getBoundsInScreen(r);
        if (n.isVisibleToUser() && !r.isEmpty() && p.test(n)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = find(n.getChild(i), p); if (hit != null) return hit;
        }
        return null;
    }
    private AccessibilityNodeInfo node(Predicate<AccessibilityNodeInfo> p) {
        return find(ui.getRootInActiveWindow(), p);
    }
    private AccessibilityNodeInfo waitNode(Predicate<AccessibilityNodeInfo> p) {
        long stop = SystemClock.uptimeMillis() + 20000;
        do { AccessibilityNodeInfo n = node(p); if (n != null) return n; SystemClock.sleep(10); } while (SystemClock.uptimeMillis() < stop);
        throw new IllegalStateException("Expected app control did not become ready");
    }
    private boolean has(String value) { return node(n -> text(n).contains(value)) != null; }
    private void home(boolean resume) {
        waitNode(n -> n.isEnabled() && text(n).contains(resume ? "Resume workout" : "Start workout"));
        long stop = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < stop) {
            if (node(n -> text(n).matches(".*\\d+/\\d+ workouts.*")) != null && !has("Loading…")) {
                SystemClock.sleep(32);
                if (!has("Loading…")) return;
            }
            SystemClock.sleep(10);
        }
        throw new IllegalStateException("Weekly progress did not become ready");
    }
    private void event(long down, long now, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(down, now, action, x, y, 0); e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        if (!ui.injectInputEvent(e, true)) throw new IllegalStateException("Touch injection failed"); e.recycle();
    }
    private void tap(AccessibilityNodeInfo n) {
        Rect r = new Rect(); n.getBoundsInScreen(r); long t = SystemClock.uptimeMillis();
        event(t,t,MotionEvent.ACTION_DOWN,r.centerX(),r.centerY()); SystemClock.sleep(10);
        event(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,r.centerX(),r.centerY());
    }
    private void scroll() {
        AccessibilityNodeInfo n = waitNode(v -> v.isScrollable()); Rect r = new Rect(); n.getBoundsInScreen(r);
        long t = SystemClock.uptimeMillis(); float x = r.centerX(), a = r.top + r.height() * .8f, b = r.top + r.height() * .2f;
        event(t,t,MotionEvent.ACTION_DOWN,x,a);
        for (int i=1;i<=8;i++) { SystemClock.sleep(20); event(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,a+(b-a)*i/8); }
        event(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,b); SystemClock.sleep(40);
    }
    private void discard() {
        AccessibilityNodeInfo n = null;
        for (int i=0;i<12;i++) { n=node(v -> text(v).contains("Discard workout")); if(n!=null)break;scroll(); }
        if(n==null)throw new IllegalStateException("Discard action not reachable");
        tap(n); tap(waitNode(v -> v.isEnabled() && v.getText()!=null && "Discard".contentEquals(v.getText()))); home(false);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            ui = getUiAutomation(); JSONArray values = new JSONArray();
            int runs = Integer.parseInt(args.getString("runs", "7"));
            boolean stats = "stats".equals(args.getString("mode", "workout"));
            int workouts = Integer.parseInt(args.getString("workouts", "1003"));
            String expectedCount = workouts + (workouts == 1 ? " workout" : " workouts");
            int warmups = Integer.parseInt(args.getString("warmups", "2"));
            for(int i=-warmups;i<runs;i++) {
                shell("am force-stop " + app); SystemClock.sleep(150);
                Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(app);
                if(intent==null) throw new IllegalStateException("App is not installed");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                long t=SystemClock.elapsedRealtimeNanos(); getContext().startActivity(intent); home(false);
                long home=SystemClock.elapsedRealtimeNanos();
                if (stats) {
                    tap(waitNode(v -> v.isEnabled() && "Stats".equals(String.valueOf(v.getText()))));
                    waitNode(v -> "Your stats".equals(String.valueOf(v.getText())));
                    waitNode(v -> expectedCount.equals(String.valueOf(v.getText()))); SystemClock.sleep(32);
                    long ready=SystemClock.elapsedRealtimeNanos();
                    if(i>=0) values.put(new JSONObject().put("run",i+1).put("home_ms",(home-t)/1e6).put("first_stats_ms",(ready-home)/1e6).put("launch_to_stats_ms",(ready-t)/1e6));
                } else {
                    tap(waitNode(v -> v.isEnabled() && text(v).contains("Start workout")));
                    waitNode(v -> v.isEnabled() && text(v).contains("Complete set 1")); SystemClock.sleep(32);
                    long session=SystemClock.elapsedRealtimeNanos();
                    if(i>=0) values.put(new JSONObject().put("run",i+1).put("home_ms",(home-t)/1e6).put("start_workout_ms",(session-home)/1e6).put("launch_to_workout_ms",(session-t)/1e6));
                    discard();
                }
            }
            File directory=new File(getTargetContext().getFilesDir(),"results"); directory.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(directory, stats ? "stats.json" : "flows.json"))) { out.write(values.toString(2).getBytes("UTF-8")); }
            result.putString("stream", "\nPERFORMANCE_OK\n"+values.toString()+"\n"); finish(-1,result);
        } catch(Throwable e) {
            StringWriter message=new StringWriter();e.printStackTrace(new PrintWriter(message));result.putString("stream",message.toString());finish(1,result);
        }
    }
}
