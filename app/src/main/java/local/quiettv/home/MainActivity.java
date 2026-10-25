package local.quiettv.home;

import android.app.Activity;
import android.app.Dialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.animation.ValueAnimator;
import android.animation.ArgbEvaluator;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Offline TV launcher. No analytics, network clients, services or package mutations. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 14, 20);
    private static final int PANEL = Color.rgb(26, 29, 39);
    private static final int ACCENT = Color.rgb(182, 199, 255);
    private static final int MUTED = Color.rgb(139, 146, 165);
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<App> apps = new ArrayList<>();
    private android.content.SharedPreferences prefs;
    private LinearLayout content, grid;
    private TextView info, sectionTitle, sectionHint;
    private TextClock clock;
    private LinearLayout root;
    private GradientDrawable backdrop;
    private ValueAnimator colorAnimation;
    private ValueAnimator carouselAnimation;
    private int previewGeneration;
    private LinearLayout heroDescription;
    private int backgroundColor = Color.rgb(25, 28, 43);
    private ImageView previewImage;
    private TextView previewTitle, previewSubtitle;
    private String previewKey = "";
    private boolean favoritesOnly = true;
    private Dialog drawer, searchDialog;
    private Button menuButton;
    private int generation = 0;
    private String lastFocus = "";
    private static class App {
        String key, name;
        ComponentName component;
        Drawable icon, banner;
        int color = Color.rgb(38, 46, 70);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("quiet-tv", MODE_PRIVATE);
        if (state != null) favoritesOnly = state.getBoolean("favorites", true);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        reloadApps();
    }

    @Override protected void onPause() {
        super.onPause();
        if (colorAnimation != null) colorAnimation.cancel();
        if (carouselAnimation != null) carouselAnimation.cancel();
        cancelPreview();
    }

    @Override protected void onDestroy() {
        generation++;
        if (colorAnimation != null) colorAnimation.cancel();
        if (carouselAnimation != null) carouselAnimation.cancel();
        cancelPreview();
        if (drawer != null) drawer.dismiss();
        if (searchDialog != null) searchDialog.dismiss();
        handler.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("favorites", favoritesOnly);
        super.onSaveInstanceState(out);
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        return t;
    }
    private GradientDrawable shape(int color, boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(20));
        d.setStroke(dp(focused ? 2 : 0), focused ? ACCENT : Color.TRANSPARENT);
        return d;
    }
    private void focusStyle(View v) {
        v.setFocusable(true);
        v.setBackground(shape(PANEL, false));
        v.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(shape(focused ? Color.rgb(48, 54, 75) : PANEL, focused));
            view.animate().cancel();
            view.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                .setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
            if (focused && view.getTag() instanceof String) lastFocus = (String)view.getTag();
        });
    }
    private Button button(String title, String tag, Runnable action) {
        Button b = new Button(this);
        b.setText(title); b.setTextSize(13); b.setAllCaps(false);
        b.setStateListAnimator(null); b.setElevation(0);
        b.setTextColor(Color.WHITE); b.setMinHeight(0); b.setMinimumHeight(0); b.setMinimumWidth(0);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setTag(tag); focusStyle(b); b.setOnClickListener(v -> action.run());
        return b;
    }
    private void headerButton(LinearLayout row, Button button, int width) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(width), dp(38));
        p.setMargins(dp(10), 0, 0, 0); row.addView(button, p);
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        backdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{backgroundColor, BG, Color.rgb(15, 19, 29)});
        root.setBackground(backdrop);
        root.setPadding(dp(48), dp(20), dp(48), dp(14));
        setContentView(root);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        menuButton = button("☰", "menu", this::showDrawer);
        menuButton.setTextSize(22);
        menuButton.setContentDescription("Открыть навигацию");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(42), dp(38));
        mp.rightMargin = dp(16); header.addView(menuButton, mp);
        TextView brand = text("Quiet TV  /  Медиа", 20, Color.WHITE);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setLetterSpacing(0.06f);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button vpn = button("VPN", "vpn", this::openVpn);
        vpn.setContentDescription("Открыть VPN. Удержание OK — выбрать приложение");
        vpn.setOnLongClickListener(v -> { chooseVpn(); return true; });
        headerButton(header, vpn, 66);
        headerButton(header, button("Настройки", "settings", this::settingsMenu), 100);
        clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTextSize(20); clock.setTextColor(MUTED);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.leftMargin = dp(22); header.addView(clock, cp);
        root.addView(header);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(10), dp(16), 0, dp(4));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        sectionTitle = text("Главная", 16, Color.WHITE);
        sectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(sectionTitle);
        sectionHint = text("Ваши приложения. Ничего лишнего.", 11, MUTED);
        titles.addView(sectionHint);
        nav.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(nav);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(dp(3), dp(3), dp(3), dp(12));
        grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        info = text("Удерживайте OK на приложении, чтобы закрепить его на главной", 12, MUTED);
        info.setPadding(0, dp(8), 0, 0); root.addView(info);
        // Initial focus moves to the first application after loading.
        lastFocus = "";
    }

    private void reloadApps() {
        final int current = ++generation;
        if (apps.isEmpty()) {
            grid.removeAllViews();
            grid.addView(text("Загружаем приложения…", 18, MUTED));
        }
        loader.execute(() -> {
            ArrayList<App> found = new ArrayList<>();
            String error = null;
            try {
                PackageManager pm = getPackageManager();
                // Prefer the TV entry point; use phone launcher only if no TV entry exists.
                LinkedHashMap<String, ResolveInfo> unique = new LinkedHashMap<>();
                for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                    Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
                    for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                        ActivityInfo ai = ri.activityInfo;
                        if (ai == null || !ai.enabled || !ai.applicationInfo.enabled ||
                            !ai.exported || ai.packageName.equals(getPackageName())) continue;
                        if (!unique.containsKey(ai.packageName)) unique.put(ai.packageName, ri);
                    }
                }
                for (ResolveInfo ri : unique.values()) {
                    App app = new App();
                    app.component = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                    app.key = app.component.flattenToString();
                    app.name = ri.loadLabel(pm).toString();
                    try { app.icon = ri.loadIcon(pm); } catch (RuntimeException ignored) { }
                    try {
                        Drawable banner = ri.activityInfo.loadBanner(pm);
                        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                        if (banner != null) app.banner = thumbnail(banner, 320, 180);
                    } catch (RuntimeException ignored) { }
                    app.color = extractColor(app.icon);
                    found.add(app);
                }
                java.text.Collator sorter = java.text.Collator.getInstance(new Locale("ru"));
                found.sort((a,b) -> sorter.compare(a.name, b.name));
            } catch (RuntimeException ex) { error = "Не удалось прочитать список приложений."; }
            final String loadError = error;
            runOnUiThread(() -> {
                if (isDestroyed() || current != generation) return;
                apps.clear(); apps.addAll(found); renderApps();
                if (loadError != null) toast(loadError);
            });
        });
    }

    private Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet("favorites", Collections.emptySet()));
    }
    private ArrayList<App> homeSuggestions() {
        ArrayList<App> selected = new ArrayList<>();
        for (App app : apps) {
            String name = (app.name + " " + app.component.getPackageName()).toLowerCase(Locale.ROOT);
            if (name.matches(".*(youtube|smarttube|kinopoisk|кинопоиск|netflix|ivi|wink|okko|vlc|rutube|рутуб|kodi|медиацентр|twitch|spotify).*")) {
                selected.add(app);
                if (selected.size() == 6) break;
            }
        }
        return selected;
    }

    private void renderApps() {
        String wanted = lastFocus;
        cancelPreview();
        if (carouselAnimation != null) carouselAnimation.cancel();
        grid.removeAllViews();
        sectionTitle.setText(favoritesOnly ? "Главная" : "Все приложения");
        grid.animate().cancel();
        grid.setAlpha(0f); grid.setTranslationY(dp(8));
        grid.animate().alpha(1f).translationY(0).setDuration(220)
            .setInterpolator(new DecelerateInterpolator()).start();
        Set<String> starred = favorites();
        ArrayList<App> visible = new ArrayList<>();
        for (App a : apps) if (!favoritesOnly || starred.contains(a.key)) visible.add(a);
        boolean suggested = favoritesOnly && starred.isEmpty();
        if (suggested) visible = homeSuggestions();
        sectionHint.setText(favoritesOnly ? (suggested ? "Закрепите любимые приложения удержанием OK" : "Ваши приложения. Ничего лишнего.") : "Выберите приложение или закрепите его на главной");
        previewImage = null; previewTitle = null; previewSubtitle = null;
        if (favoritesOnly && !visible.isEmpty()) {
            renderCarousel(visible, wanted);
            return;
        }
        if (visible.isEmpty()) {
            TextView empty = text(favoritesOnly
                ? "Добавьте любимые приложения из раздела «Все приложения»: удерживайте OK на нужной плитке."
                : "Приложения не найдены. Обновите список через настройки.", 18, MUTED);
            empty.setPadding(dp(8), dp(30), dp(8), dp(30)); grid.addView(empty);
        }
        int columns = favoritesOnly ? 3 : 4;
        int iconSize = favoritesOnly ? 100 : 66;
        int height = favoritesOnly ? 172 : 140;
        LinearLayout row = null;
        View restore = null, first = null;
        for (int i=0; i<visible.size(); i++) {
            App app = visible.get(i);
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false); row.setClipToPadding(false);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(14), dp(12), dp(14), dp(12));
            tile.setTag(app.key); tile.setContentDescription(app.name +
                (starred.contains(app.key) ? ", на главной" : ""));
            focusStyle(tile);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon != null ? app.icon : getDrawable(local.quiettv.home.R.drawable.icon));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
            TextView label = text(app.name, favoritesOnly ? 17 : 15, Color.WHITE);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setGravity(Gravity.CENTER); label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin=dp(12); tile.addView(label, lp);
            if (i % columns == 0) tile.setOnKeyListener((v, code, event) -> {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) showDrawer();
                    return true;
                }
                return false;
            });
            tile.setOnClickListener(v -> launch(app.component));
            tile.setOnLongClickListener(v -> { appMenu(app); return true; });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(height), 1);
            p.setMargins(dp(5), dp(5), dp(5), dp(5)); row.addView(tile, p);
            if (first == null) first = tile;
            if (app.key.equals(wanted)) restore=tile;
        }
        if (row != null) for (int i=visible.size()%columns; i>0 && i<columns; i++) {
            View spacer = new View(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1);
            p.setMargins(dp(5), dp(5), dp(5), dp(5)); row.addView(spacer,p);
        }
        if (drawer != null || searchDialog != null) return;
        if (restore != null) restore.requestFocus();
        else if (wanted.isEmpty() && first != null) first.requestFocus();
        else if (getCurrentFocus() == null) menuButton.requestFocus();
    }

    private Drawable thumbnail(Drawable source, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(source.getBounds());
        int sw = Math.max(1, source.getIntrinsicWidth()), sh = Math.max(1, source.getIntrinsicHeight());
        float scale = Math.min(width / (float)sw, height / (float)sh);
        int w = Math.round(sw * scale), h = Math.round(sh * scale);
        source.setBounds((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2);
        source.draw(new Canvas(bitmap)); source.setBounds(bounds);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private int extractColor(Drawable icon) {
        if (icon == null) return Color.rgb(38, 46, 70);
        try {
            Bitmap b = ((BitmapDrawable)thumbnail(icon, 24, 24)).getBitmap();
            double red=0, green=0, blue=0, weight=0;
            float[] hsv = new float[3];
            for (int y=0; y<24; y++) for (int x=0; x<24; x++) {
                int c=b.getPixel(x,y); if (Color.alpha(c)<160) continue;
                Color.colorToHSV(c,hsv);
                if (hsv[1]<0.22f || hsv[2]<0.18f) continue;
                double w=hsv[1]*hsv[2];
                red+=Color.red(c)*w; green+=Color.green(c)*w; blue+=Color.blue(c)*w; weight+=w;
            }
            b.recycle();
            if (weight==0) return Color.rgb(38, 46, 70);
            Color.colorToHSV(Color.rgb((int)(red/weight),(int)(green/weight),(int)(blue/weight)),hsv);
            hsv[1]=Math.min(0.65f, Math.max(0.35f,hsv[1])); hsv[2]=0.28f;
            return Color.HSVToColor(hsv);
        } catch (RuntimeException e) { return Color.rgb(38, 46, 70); }
    }

    private void cancelPreview() {
        previewGeneration++;
        if (previewImage != null) previewImage.animate().cancel();
        if (heroDescription != null) heroDescription.animate().cancel();
    }

    private void showPreview(App app) {
        if (previewImage == null) return;
        boolean first = previewTitle.getText().length() == 0;
        if (!first && app.key.equals(previewKey)) return;
        cancelPreview();
        previewKey = app.key;
        final int revision = previewGeneration;
        final ImageView image = previewImage;
        final LinearLayout description = heroDescription;
        Runnable update = () -> {
            if (revision != previewGeneration || !image.isAttachedToWindow()) return;
            previewTitle.setText(app.name);
            previewSubtitle.setText("Ваше пространство для просмотра и музыки\nOK — открыть  ·  Удержание OK — действия");
            image.setImageDrawable(app.banner != null ? app.banner :
                (app.icon != null ? app.icon : getDrawable(R.drawable.icon)));
            int padding = dp(app.banner != null ? 8 : 32);
            image.setPadding(padding, padding, padding, padding);
            image.setAlpha(0f); image.setTranslationX(dp(16));
            description.setAlpha(0f); description.setTranslationY(dp(10));
            image.animate().alpha(1f).translationX(0).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
            description.animate().alpha(1f).translationY(0).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
        };
        if (first) image.post(update);
        else {
            description.animate().alpha(0f).setDuration(90).start();
            image.animate().alpha(0f).setDuration(90).withEndAction(update).start();
        }
        if (colorAnimation != null) colorAnimation.cancel();
        colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), backgroundColor, app.color);
        colorAnimation.setDuration(420);
        colorAnimation.setInterpolator(new DecelerateInterpolator());
        colorAnimation.addUpdateListener(animation -> {
            backgroundColor = (Integer)animation.getAnimatedValue();
            backdrop.setColors(new int[]{backgroundColor, BG, Color.rgb(15,19,29)});
        });
        colorAnimation.start();
    }

    /** Keep focus scrolling under one animator, including rapid repeated D-pad presses. */
    private class AppCarousel extends HorizontalScrollView {
        AppCarousel() { super(MainActivity.this); }
        @Override public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
            return false;
        }
        void reveal(View tile) {
            if (carouselAnimation != null) carouselAnimation.cancel();
            int maximum = Math.max(0, getChildAt(0).getWidth() - getWidth());
            int target = Math.max(0, Math.min(maximum, tile.getLeft() - dp(18)));
            carouselAnimation = ValueAnimator.ofInt(getScrollX(), target);
            carouselAnimation.setDuration(280);
            carouselAnimation.setInterpolator(new DecelerateInterpolator());
            carouselAnimation.addUpdateListener(a -> scrollTo((Integer)a.getAnimatedValue(), 0));
            carouselAnimation.start();
        }
    }

    private void renderCarousel(ArrayList<App> visible, String wanted) {
        grid.setClipChildren(false); grid.setClipToPadding(false);
        AppCarousel carousel = new AppCarousel();
        carousel.setHorizontalScrollBarEnabled(false); carousel.setClipToPadding(false);
        carousel.setOverScrollMode(View.OVER_SCROLL_NEVER);
        carousel.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout row = new LinearLayout(this); row.setClipChildren(false); row.setClipToPadding(false);
        carousel.addView(row); grid.addView(carousel, new LinearLayout.LayoutParams(-1, dp(132)));

        int screenHeight = getResources().getConfiguration().screenHeightDp;
        int screenWidth = getResources().getConfiguration().screenWidthDp;
        int heroHeight = Math.max(150, Math.min(340, screenHeight - 282));
        LinearLayout hero = new LinearLayout(this); hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(12), dp(12), dp(12));
        heroDescription = new LinearLayout(this); heroDescription.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("ВАШИ ПРИЛОЖЕНИЯ", 11, ACCENT);
        eyebrow.setLetterSpacing(0.16f); heroDescription.addView(eyebrow);
        previewTitle = text("", screenHeight < 500 ? 30 : 42, Color.WHITE);
        previewTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        previewTitle.setMaxLines(2); previewTitle.setEllipsize(TextUtils.TruncateAt.END);
        previewTitle.setPadding(0, dp(12), 0, dp(16)); heroDescription.addView(previewTitle);
        previewSubtitle = text("", 13, MUTED);
        previewSubtitle.setLineSpacing(dp(6), 1f); heroDescription.addView(previewSubtitle);
        hero.addView(heroDescription, new LinearLayout.LayoutParams(0, -2, 1));
        previewImage = new ImageView(this); previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(previewImage, new LinearLayout.LayoutParams(dp(Math.min(420, screenWidth * 2 / 5)), -1));
        grid.addView(hero, new LinearLayout.LayoutParams(-1, dp(heroHeight)));

        View first = null, restore = null;
        App initial = visible.get(0);
        for (int i=0; i<visible.size(); i++) {
            App app = visible.get(i);
            LinearLayout tile = new LinearLayout(this);
            tile.setGravity(Gravity.CENTER); tile.setPadding(dp(10), dp(10), dp(10), dp(10));
            tile.setTag(app.key); tile.setContentDescription(app.name); focusStyle(tile);
            ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageDrawable(app.icon != null ? app.icon : getDrawable(R.drawable.icon));
            tile.addView(icon, new LinearLayout.LayoutParams(-1, -1));
            tile.setOnFocusChangeListener((view, focused) -> {
                view.setBackground(shape(focused ? Color.rgb(58,65,82) : PANEL, focused));
                view.animate().cancel();
                view.animate().scaleX(focused ? 1.16f : 1f).scaleY(focused ? 1.16f : 1f)
                    .translationY(focused ? dp(2) : 0).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
                if (focused) {
                    lastFocus = app.key; showPreview(app);
                    carousel.post(() -> {
                        if (view.isAttachedToWindow() && view.hasFocus()) carousel.reveal(view);
                    });
                }
            });
            tile.setOnHoverListener((view,event) -> {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) view.requestFocus();
                return false;
            });
            if (i == 0) tile.setOnKeyListener((view,key,event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_LEFT) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) showDrawer();
                return true;
            });
            tile.setOnClickListener(v -> launch(app.component));
            tile.setOnLongClickListener(v -> { appMenu(app); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(88));
            lp.setMargins(dp(8), dp(6), dp(8), dp(6)); row.addView(tile, lp);
            if (first == null) first = tile;
            if (app.key.equals(previewKey)) initial = app;
            if (app.key.equals(wanted)) { restore = tile; initial = app; }
        }
        showPreview(initial);
        if (drawer != null || searchDialog != null) return;
        if (restore != null) restore.requestFocus();
        else if (wanted.isEmpty() && first != null) first.requestFocus();
        else if (getCurrentFocus() == null) menuButton.requestFocus();
    }


    private void openPlayStore() {
        Intent store = getPackageManager().getLeanbackLaunchIntentForPackage("com.android.vending");
        if (store == null) store = getPackageManager().getLaunchIntentForPackage("com.android.vending");
        if (store != null) { open(store); return; }
        new AlertDialog.Builder(this).setTitle("Google Play недоступен")
            .setMessage("Магазин не найден или отключён. Quiet TV не устанавливает сервисы Google. Для проверки совместимости нужна информация о прошивке и установленных системных приложениях.")
            .setPositiveButton("Понятно",null).show();
    }

    private void showDrawer() {
        if (drawer != null || searchDialog != null) return;
        final View previous = getCurrentFocus();
        final String previousKey = lastFocus;
        Dialog dialog = new Dialog(this);
        drawer = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(20), dp(24), dp(16));
        panel.setBackgroundColor(Color.rgb(20, 23, 34));
        TextView brand = text("quiet tv", 24, Color.WHITE);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(brand);
        TextView subtitle = text("Ваше пространство", 13, MUTED);
        subtitle.setPadding(0, dp(6), 0, dp(20)); panel.addView(subtitle);
        Button search = drawerButton("Поиск", "drawer-search", () -> {
            dialog.dismiss(); showSearch();
        });
        Button home = drawerButton("Главная", "drawer-home", () -> {
            dialog.dismiss(); favoritesOnly = true; lastFocus = ""; renderApps();
        });
        Button spotify = drawerButton("Spotify", "drawer-spotify", () -> {
            dialog.dismiss(); openSpotify();
        });
        setDrawerIcon(search, R.drawable.nav_search);
        setDrawerIcon(home, R.drawable.nav_home);
        setDrawerIcon(spotify, R.drawable.nav_music);
        spotify.setTextColor(Color.rgb(100, 221, 145));
        for (Button button : new Button[]{search, home, spotify}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
            lp.bottomMargin = dp(8); panel.addView(button, lp);
        }
        panel.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        Button play = drawerButton("Google Play", "drawer-play", () -> {
            dialog.dismiss(); openPlayStore();
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(48));
        pl.bottomMargin = dp(8); panel.addView(play, pl);
        Button all = drawerButton("Все приложения", "drawer-all", () -> {
            dialog.dismiss(); favoritesOnly = false; lastFocus = ""; renderApps();
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(48));
        al.bottomMargin = dp(14); panel.addView(all, al);
        TextView help = text("Назад или → — закрыть", 12, MUTED);
        panel.addView(help);
        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> {
            drawer = null; lastFocus = previousKey;
            if (!isFinishing() && !isDestroyed()) {
                if (previous != null && previous.isAttachedToWindow()) previous.requestFocus();
                else menuButton.requestFocus();
            }
        });
        dialog.setOnKeyListener((d, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_MENU) {
                if (event.getAction() == KeyEvent.ACTION_UP) dialog.dismiss();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            return false;
        });
        dialog.getWindow().setWindowAnimations(R.style.DrawerAnimation);
        dialog.show();
        Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.62f);
        window.setGravity(Gravity.LEFT | Gravity.TOP);
        window.setLayout(dp(270), WindowManager.LayoutParams.MATCH_PARENT);
        window.setWindowAnimations(R.style.DrawerAnimation);
        search.requestFocus();
    }

    private void setDrawerIcon(Button button, int resource) {
        Drawable icon = getDrawable(resource);
        icon.setBounds(0, 0, dp(24), dp(24));
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(16));
    }

    private Button drawerButton(String title, String tag, Runnable action) {
        Button b = button(title, tag, action);
        b.setTextSize(20); b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        b.setPadding(dp(18), 0, dp(12), 0);
        return b;
    }

    private void openSpotify() {
        for (String pkg : new String[]{"com.spotify.tv.android", "com.spotify.music"}) {
            for (App app : apps) if (app.component.getPackageName().equals(pkg)) {
                launch(app.component); return;
            }
        }
        new AlertDialog.Builder(this).setTitle("Spotify не найден")
            .setMessage("Установите Spotify на телевизор и вернитесь в Quiet TV. Кнопка откроет установленное приложение; аккаунт и воспроизведение управляются внутри Spotify.")
            .setPositiveButton("Понятно", null).show();
    }

    private void showSearch() {
        if (searchDialog != null) return;
        final String previousKey = lastFocus;
        final View previous = getCurrentFocus();
        Dialog dialog = new Dialog(this);
        searchDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(22), dp(24), dp(20));
        panel.setBackground(shape(Color.rgb(20, 23, 34), false));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Поиск приложений", 24, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("Закрыть", "search-close", dialog::dismiss);
        heading.addView(close, new LinearLayout.LayoutParams(dp(100), dp(40))); panel.addView(heading);
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setTextColor(Color.WHITE); input.setHintTextColor(MUTED);
        input.setTextSize(18); input.setHint("Название приложения");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(56));
        ep.topMargin = dp(12); ep.bottomMargin = dp(12); panel.addView(input, ep);
        ScrollView resultsScroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        resultsScroll.addView(results); panel.addView(resultsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        ArrayList<App> snapshot = new ArrayList<>(apps);
        Runnable update = () -> {
            results.removeAllViews();
            String query = input.getText().toString().trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
            for (App app : snapshot) {
                if (!app.name.toLowerCase(Locale.ROOT).replace('ё', 'е').contains(query)) continue;
                Button result = button(app.name, "search-result", () -> { dialog.dismiss(); launch(app.component); });
                result.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                result.setTextSize(17);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(50));
                rp.bottomMargin = dp(8); results.addView(result, rp);
            }
            if (results.getChildCount() == 0) {
                TextView empty = text("Ничего не найдено", 17, MUTED);
                empty.setPadding(dp(8), dp(12), dp(8), dp(12)); results.addView(empty);
            }
        };
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            public void afterTextChanged(Editable e) { }
        });
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if (ime != null) ime.hideSoftInputFromWindow(input.getWindowToken(), 0);
                if (results.getChildCount() > 0 && results.getChildAt(0).isFocusable()) results.getChildAt(0).requestFocus();
                return true;
            }
            return false;
        });
        dialog.setContentView(panel);
        dialog.setOnDismissListener(d -> {
            searchDialog = null; lastFocus = previousKey;
            if (!isFinishing() && !isDestroyed()) {
                if (previous != null && previous.isAttachedToWindow()) previous.requestFocus();
                else menuButton.requestFocus();
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.62f);
        int width = Math.min(dp(650), getResources().getDisplayMetrics().widthPixels - dp(64));
        int height = getResources().getDisplayMetrics().heightPixels - dp(64);
        window.setLayout(width, height);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        update.run(); input.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) showDrawer();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && getCurrentFocus() == menuButton) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) showDrawer();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void appMenu(App app) {
        boolean star = favorites().contains(app.key);
        new AlertDialog.Builder(this).setTitle(app.name).setItems(new String[]{
            star ? "Убрать с главной" : "Закрепить на главной",
            "Назначить кнопке VPN", "Сведения о приложении"
        }, (d, which) -> {
            if (which == 0) {
                Set<String> values = favorites();
                if (star) values.remove(app.key); else values.add(app.key);
                prefs.edit().putStringSet("favorites", values).apply(); renderApps();
            } else if (which == 1) {
                prefs.edit().putString("vpn", app.key).apply();
                toast("Кнопка VPN откроет: " + app.name);
            } else {
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + app.component.getPackageName())));
            }
        }).setNegativeButton("Закрыть", null).show();
    }
    private void chooseVpn() {
        if (apps.isEmpty()) { toast("Сначала установите VPN-приложение."); return; }
        String[] names = new String[apps.size()+1];
        names[0] = "Сбросить назначение";
        ArrayList<App> snapshot = new ArrayList<>(apps);
        for (int i=0; i<snapshot.size(); i++) names[i+1]=snapshot.get(i).name;
        new AlertDialog.Builder(this).setTitle("Какое приложение открывать кнопкой VPN?")
            .setItems(names, (d, n) -> {
                if (n==0) prefs.edit().remove("vpn").apply();
                else {
                    prefs.edit().putString("vpn", snapshot.get(n-1).key).apply();
                    toast("Назначено: " + snapshot.get(n-1).name);
                }
            }).setNegativeButton("Отмена", null).show();
    }
    private void openVpn() {
        ComponentName c = ComponentName.unflattenFromString(prefs.getString("vpn", ""));
        if (c == null) chooseVpn(); else launch(c);
    }
    private void launch(ComponentName c) {
        Intent i = new Intent(Intent.ACTION_MAIN).setComponent(c)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        open(i);
    }
    private boolean open(Intent i) {
        try { startActivity(i); return true; }
        catch (ActivityNotFoundException | SecurityException e) {
            toast("Недоступно на этой прошивке или приложение удалено."); return false;
        }
    }
    private void settingsMenu() {
        new AlertDialog.Builder(this).setTitle("Quiet TV").setItems(new String[]{
            "Настройки телевизора", "Приложения и память", "Выбрать VPN-приложение",
            "Сделать главным экраном", "Выбор другого главного экрана",
            "Обновить список приложений", "О приложении и приватности"
        }, (d,n) -> {
            switch(n) {
                case 0: open(new Intent(Settings.ACTION_SETTINGS)); break;
                case 1: if (!open(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)))
                    open(new Intent(Settings.ACTION_SETTINGS)); break;
                case 2: chooseVpn(); break;
                case 3: requestHome(); break;
                case 4: open(new Intent(Settings.ACTION_HOME_SETTINGS)); break;
                case 5: reloadApps(); break;
                case 6: about(); break;
            }
        }).setNegativeButton("Закрыть",null).show();
    }
    private void requestHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (rm.isRoleHeld(RoleManager.ROLE_HOME)) { toast("Quiet TV уже выбран."); return; }
                try {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 10);
                    return;
                } catch (RuntimeException ignored) { }
            }
        }
        if (!open(new Intent(Settings.ACTION_HOME_SETTINGS)))
            new AlertDialog.Builder(this).setTitle("Выбор оболочки ограничен")
                .setMessage("Прошивка не предоставляет выбор главного экрана. Quiet TV можно открывать как приложение. Сначала соберите диагностику с компьютера — штатную оболочку удалять не нужно.")
                .setPositiveButton("Понятно",null).show();
    }
    private String storageText() {
        try {
            StatFs fs = new StatFs(getFilesDir().getAbsolutePath());
            return String.format(new Locale("ru"), "Свободно %.1f ГБ из %.1f ГБ · Quiet TV 0.5.0-dev",
                fs.getAvailableBytes()/1073741824.0, fs.getTotalBytes()/1073741824.0);
        } catch (RuntimeException e) { return "Quiet TV 0.5.0-dev"; }
    }
    private void about() {
        new AlertDialog.Builder(this).setTitle("Quiet TV 0.5.0-dev — новый главный экран")
            .setMessage("Оболочка не имеет разрешения на интернет, не содержит рекламы и аналитики. Избранное и назначение VPN сохраняются только на ТВ.\n\nКнопка VPN открывает установленный клиент: подключение выполняется в нём.\n\nОболочка не удаляет телеметрию Android или других приложений, не очищает оперативную память и не меняет прошивку." + "\n\n" + storageText())
            .setPositiveButton("Понятно", null).show();
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() {
        if (!favoritesOnly) { favoritesOnly=true; renderApps(); menuButton.requestFocus(); }
        else new AlertDialog.Builder(this).setTitle("Закрыть Quiet TV?")
            .setMessage("Если это главный экран по умолчанию, система может открыть его снова.")
            .setPositiveButton("Закрыть",(d,n)->finish())
            .setNegativeButton("Остаться",null).show();
    }
}
