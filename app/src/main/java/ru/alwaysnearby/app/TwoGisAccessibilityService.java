package ru.alwaysnearby.app;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.Intent;
import android.os.Bundle;
import java.util.LinkedHashSet;
import java.util.Set;

public class TwoGisAccessibilityService extends AccessibilityService {
    public static final String ACTION_VISIBLE_TEXT = "ru.alwaysnearby.VISIBLE_2GIS_TEXT";
    public static final String EXTRA_TEXT = "visible_text";
    private long lastSentAt = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null ||
            !"ru.dublgis.dgismobile".contentEquals(event.getPackageName())) return;

        long now = System.currentTimeMillis();
        if (now - lastSentAt < 700) return;
        lastSentAt = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        Set<String> lines = new LinkedHashSet<>();
        collectVisibleText(root, lines, 0);
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (result.length() > 0) result.append("\n");
            result.append(line);
            if (result.length() > 4000) break;
        }

        Intent intent = new Intent(ACTION_VISIBLE_TEXT);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_TEXT, result.toString());
        sendBroadcast(intent);
    }

    private void collectVisibleText(AccessibilityNodeInfo node, Set<String> lines, int depth) {
        if (node == null || depth > 30 || lines.size() > 150) return;
        if (node.isVisibleToUser()) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            add(lines, text);
            add(lines, desc);
            for (int i = 0; i < node.getChildCount(); i++) {
                collectVisibleText(node.getChild(i), lines, depth + 1);
            }
        }
    }

    private void add(Set<String> lines, CharSequence value) {
        if (value == null) return;
        String s = value.toString().trim();
        if (!s.isEmpty() && s.length() < 300) lines.add(s);
    }

    @Override public void onInterrupt() { }
}