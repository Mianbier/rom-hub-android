/*
 * This file is part of ROM Hub, released under the GNU Affero General Public License v3.0.
 *
 * 本文件移植 / 改写自 HyperCeiler（AGPL-3.0，Copyright (C) 2023-2026 HyperCeiler
 * Contributions），或为其等价替身实现 —— 完整来源与鸣谢见项目根目录 NOTICE.md。
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.linbaogu.romhub.hc.provision.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BulletSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fan.internal.utils.AttributeResolver;

public class MarkdownView extends TextView {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnMarkdownLoadListener loadListener;

    // 样式配置
    private static int COLOR_TITLE = Color.parseColor("#333333");
    private static int COLOR_TEXT = Color.parseColor("#444444");
    private static final int COLOR_LINK = Color.parseColor("#1A73E8");
    private static final int COLOR_BULLET = Color.parseColor("#888888");
    private static final int COLOR_DIVIDER = Color.parseColor("#CCCCCC");

    private static String currentUrl;

    // 定义回调接口
    public interface OnMarkdownLoadListener {
        void onResult(boolean success);
    }

    public void setOnMarkdownLoadListener(OnMarkdownLoadListener listener) {
        this.loadListener = listener;
    }

    public MarkdownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        COLOR_TITLE = AttributeResolver.resolveColor(context, fan.appcompat.R.attr.textColorList);
        COLOR_TEXT = AttributeResolver.resolveColor(context, fan.appcompat.R.attr.textColorListSecondary);
        init();
    }

    private void init() {
        this.setTextSize(16);
        this.setLineSpacing(0, 1.6f);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        this.setPadding(padding, padding, padding, padding);
        this.setMovementMethod(LinkMovementMethod.getInstance());
        this.setHighlightColor(Color.TRANSPARENT);
    }

    /**
     * 加载在线 MD 文件
     */
    public void loadMarkdownFromUrl(final String urlString) {
        currentUrl = urlString;

        // 云端协议：legal://terms / legal://privacy
        // 内容优先取云端（站长在管理端改一次，所有客户端都能拿到），
        // 云端读不到就用本地缓存，再不行才退回 APK 内置的那份 —— 永不白屏。
        if (urlString != null && urlString.startsWith("legal://")) {
            final String which = urlString.substring("legal://".length());
            final org.linbaogu.romhub.core.LegalRepo.Doc doc =
                "privacy".equals(which)
                    ? org.linbaogu.romhub.core.LegalRepo.Doc.PRIVACY
                    : org.linbaogu.romhub.core.LegalRepo.Doc.TERMS;
            new Thread(() -> {
                String content = null;
                try {
                    content = org.linbaogu.romhub.core.LegalRepo.INSTANCE.text(getContext(), doc);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final String rawContent = content;
                mainHandler.post(() -> {
                    if (rawContent != null && !rawContent.trim().isEmpty()) {
                        render(rawContent);
                    } else {
                        String fallbackTemplate = getContext().getString(org.linbaogu.romhub.R.string.provision_get_md_failed);
                        render(fallbackTemplate.replace("%URL%", urlString));
                    }
                    if (loadListener != null) loadListener.onResult(true);
                });
            }).start();
            return;
        }

        // 本地资源：asset://romhub_terms.md —— 协议与隐私声明打包在 APK 里，
        // 不依赖网络、也不指向第三方地址。
        if (urlString != null && urlString.startsWith("asset://")) {
            final String assetName = urlString.substring("asset://".length());
            new Thread(() -> {
                String content = null;
                try (java.io.InputStream is = getContext().getAssets().open(assetName);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    content = sb.toString();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final String rawContent = content;
                mainHandler.post(() -> {
                    if (rawContent != null) {
                        render(rawContent);
                    } else {
                        String fallbackTemplate = getContext().getString(org.linbaogu.romhub.R.string.provision_get_md_failed);
                        render(fallbackTemplate.replace("%URL%", assetName));
                    }
                    if (loadListener != null) loadListener.onResult(true);
                });
            }).start();
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();

        if (!isConnected) {
            String fallbackTemplate = getContext().getString(org.linbaogu.romhub.R.string.provision_get_md_failed);
            String fallbackContent = fallbackTemplate.replace("%URL%", currentUrl);
            mainHandler.post(() -> {
                render(fallbackContent);
                if (loadListener != null) loadListener.onResult(true);
            });
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> future = executor.submit(() -> {
            boolean success = false;
            try {
                URL url = new URL(currentUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    conn.disconnect();

                    final String rawContent = sb.toString();
                    mainHandler.post(() -> {
                        render(rawContent);
                        if (loadListener != null) loadListener.onResult(true);
                    });
                    success = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!success) {
                mainHandler.post(() -> {
                    String fallbackTemplate = getContext().getString(org.linbaogu.romhub.R.string.provision_get_md_failed);
                    String fallbackContent = fallbackTemplate.replace("%URL%", currentUrl);
                    render(fallbackContent);
                    if (loadListener != null) loadListener.onResult(true);
                });
            }
        });

        executor.shutdown();

        new Thread(() -> {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                mainHandler.post(() -> {
                    String fallbackTemplate = getContext().getString(org.linbaogu.romhub.R.string.provision_get_md_failed);
                    String fallbackContent = fallbackTemplate.replace("%URL%", currentUrl);
                    render(fallbackContent);
                    if (loadListener != null) loadListener.onResult(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void render(String rawText) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] lines = rawText.split("\\r?\\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            int start = ssb.length();

            // 1. 分割线渲染 (---)
            if (trimmedLine.length() >= 3 && trimmedLine.replaceAll("[-*_ ]", "").isEmpty()) {
                ssb.append(" \n");
                ssb.setSpan(new HorizontalLineSpan(COLOR_DIVIDER), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.append("\n");
                continue;
            }

            // 2. 标题渲染 (## )
            if (line.startsWith("## ")) {
                String content = line.substring(3).trim();
                ssb.append(content).append("\n\n");
                int end = ssb.length() - 1;
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(20, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(COLOR_TITLE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // 3. 列表项 (- )
            else if (trimmedLine.startsWith("- ")) {
                renderListItem(ssb, line, start);
            }
            // 4. 普通文本
            else {
                if (trimmedLine.isEmpty()) {
                    ssb.append("\n");
                } else {
                    ssb.append(line).append("\n");
                    ssb.setSpan(new ForegroundColorSpan(COLOR_TEXT), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    processInlineStyles(ssb, start, ssb.length());
                }
            }
        }
        setText(ssb);
    }

    private void renderListItem(SpannableStringBuilder ssb, String line, int start) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') indent++;
        String content = line.trim().substring(2);
        ssb.append(content).append("\n");
        int end = ssb.length();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            ssb.setSpan(new BulletSpan(30, COLOR_BULLET, 6), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            ssb.setSpan(new BulletSpan(30), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        ssb.setSpan(new LeadingMarginSpan.Standard(30 + (indent / 2 * 40)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        processInlineStyles(ssb, start, end);
    }

    private void processInlineStyles(SpannableStringBuilder ssb, int start, int end) {
        String sub = ssb.subSequence(start, end).toString();
        // 链接
        Pattern lp = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
        Matcher lm = lp.matcher(sub);
        List<Object[]> links = new ArrayList<>();
        while (lm.find()) links.add(new Object[]{lm.start(), lm.end(), lm.group(1), lm.group(2)});
        for (int i = links.size() - 1; i >= 0; i--) {
            Object[] info = links.get(i);
            int mS = start + (int)info[0], mE = start + (int)info[1];
            ssb.replace(mS, mE, (String)info[2]);
            ssb.setSpan(new CustomClickableSpan((String)info[3]), mS, mS + ((String)info[2]).length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // 加粗
        sub = ssb.subSequence(start, ssb.length()).toString();
        Pattern bp = Pattern.compile("\\*\\*(.*?)\\*\\*");
        Matcher bm = bp.matcher(sub);
        List<int[]> bolds = new ArrayList<>();
        while (bm.find()) bolds.add(new int[]{bm.start(), bm.end()});
        for (int i = bolds.size() - 1; i >= 0; i--) {
            int[] r = bolds.get(i);
            int bS = start + r[0], bE = start + r[1];
            ssb.setSpan(new StyleSpan(Typeface.BOLD), bS, bE, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.delete(bE - 2, bE); ssb.delete(bS, bS + 2);
        }
    }

    private record HorizontalLineSpan(int color) implements LeadingMarginSpan {
        @Override
        public int getLeadingMargin(boolean first) {
            return 0;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int bsl, int bot, CharSequence txt, int s, int e, boolean f, Layout l) {
                int old = p.getColor();
            p.setColor(color);
                c.drawRect(x, (top + bot) / 2f - 2f, x + l.getWidth(), (top + bot) / 2f + 2f, p);
                p.setColor(old);
            }
        }

    private class CustomClickableSpan extends ClickableSpan {
        private final String url;
        CustomClickableSpan(String url) { this.url = url; }

        @Override
        public void onClick(@NonNull View w) {
            if ("hyperceiler://refresh".equals(url)) {
                if (currentUrl != null) {
                    TermsAndStatementBottomSheet.loadMarkdown(currentUrl);
                    TermsAndStatementBottomSheet.initView();
                }
                return;
            }
            try {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds); ds.setColor(COLOR_LINK); ds.setUnderlineText(true);
        }
    }
}
