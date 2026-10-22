package com.alfredo.appextractor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private boolean dark;
    private String lang;
    private int bg, card, fg, muted, accent, stroke;
    private LinearLayout root;
    private EditText search;
    private GridView grid;
    private TextView count, empty;
    private Button selectAll, uninstall, extract;
    private final List<AppItem> apps = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private AppAdapter adapter;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        dark = prefs.getBoolean("dark", false);
        lang = prefs.getString("lang", "ar");
        colors();
        buildUi();
        loadApps();
    }

    private boolean ar() { return "ar".equals(lang); }
    private String tr(String a, String e) { return ar() ? a : e; }
    private int dp(float x) { return (int)(x * getResources().getDisplayMetrics().density + .5f); }

    private void colors() {
        bg = dark ? Color.rgb(15,19,27) : Color.rgb(245,247,251);
        card = dark ? Color.rgb(27,34,46) : Color.WHITE;
        fg = dark ? Color.rgb(243,246,252) : Color.rgb(29,37,51);
        muted = dark ? Color.rgb(160,171,190) : Color.rgb(105,116,134);
        accent = dark ? Color.rgb(91,139,255) : Color.rgb(57,119,246);
        stroke = dark ? Color.rgb(48,59,76) : Color.rgb(226,231,240);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private GradientDrawable shape(int color, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(8));
        root.setBackgroundColor(bg);
        root.setLayoutDirection(ar() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(tr("مستخرج التطبيقات","App Extractor"));
        title.setTextColor(fg); title.setTextSize(22); title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView settings = new TextView(this);
        settings.setText("⚙"); settings.setTextSize(24); settings.setTextColor(fg);
        settings.setGravity(Gravity.CENTER); settings.setBackground(shape(card,14,stroke));
        header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(46)));
        settings.setOnClickListener(v -> settingsDialog());
        root.addView(header);

        search = new EditText(this);
        search.setSingleLine(true); search.setTextSize(15); search.setTextColor(fg);
        search.setHintTextColor(muted); search.setHint(tr("ابحث عن تطبيق...","Search apps..."));
        search.setPadding(dp(12),0,dp(12),0); search.setBackground(shape(card,14,stroke));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,dp(50)); sp.topMargin=dp(8);
        root.addView(search,sp);

        count = new TextView(this); count.setTextColor(muted); count.setTextSize(12);
        count.setPadding(dp(2),dp(10),dp(2),dp(6)); root.addView(count);

        FrameLayout holder = new FrameLayout(this);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,0,1); hp.topMargin=dp(2); root.addView(holder,hp);
        grid = new GridView(this); grid.setNumColumns(2); grid.setHorizontalSpacing(dp(10)); grid.setVerticalSpacing(dp(10));
        grid.setSelector(android.R.color.transparent); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        holder.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        empty = new TextView(this); empty.setText(tr("ما لقينا تطبيقات مطابقة","No matching apps found"));
        empty.setTextColor(muted); empty.setGravity(Gravity.CENTER); empty.setVisibility(View.GONE);
        holder.addView(empty,new FrameLayout.LayoutParams(-1,-1));

        TextView hint = new TextView(this);
        hint.setText(tr("اضغط على التطبيق للخيارات، أو على الدائرة للتحديد","Tap an app for actions, or its circle to select"));
        hint.setTextSize(11); hint.setTextColor(muted); hint.setPadding(0,dp(8),0,dp(7)); root.addView(hint);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        selectAll = button(tr("تحديد الكل","Select all"));
        uninstall = button(tr("إلغاء التثبيت","Uninstall"));
        extract = button(tr("استخراج APK","Extract APK"));
        row.addView(selectAll,new LinearLayout.LayoutParams(0,dp(46),1));
        row.addView(uninstall,new LinearLayout.LayoutParams(0,dp(46),1));
        row.addView(extract,new LinearLayout.LayoutParams(0,dp(46),1));
        root.addView(row);
        selectAll.setOnClickListener(v -> {
            List<AppItem> filtered = filteredApps();
            boolean all = !filtered.isEmpty() && filtered.stream().allMatch(a -> selected.contains(a.pkg));
            for (AppItem a : filtered) { if (all) selected.remove(a.pkg); else selected.add(a.pkg); }
            refresh();
        });
        uninstall.setOnClickListener(v -> uninstallSelected());
        extract.setOnClickListener(v -> exportSelected());
        adapter = new AppAdapter(); grid.setAdapter(adapter);
        grid.setOnItemClickListener((p,v,pos,id) -> actions(adapter.getItem(pos)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { refresh(); }
            public void afterTextChanged(Editable e) {}
        });
    }

    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(11); b.setAllCaps(false);
        b.setTextColor(fg); b.setBackground(shape(card,12,stroke)); return b;
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        List<PackageInfo> list;
        if (Build.VERSION.SDK_INT >= 33) list = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA));
        else list = pm.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pi : list) {
            try {
                ApplicationInfo ai = pi.applicationInfo;
                if (ai == null) continue;
                String label = String.valueOf(pm.getApplicationLabel(ai));
                Drawable icon = pm.getApplicationIcon(ai);
                apps.add(new AppItem(pi.packageName,label,icon));
            } catch (Exception ignored) {}
        }
        apps.sort((a,b) -> a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT)));
        refresh();
    }

    private List<AppItem> filteredApps() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<AppItem> out = new ArrayList<>();
        for (AppItem a : apps) if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q) || a.pkg.toLowerCase(Locale.ROOT).contains(q)) out.add(a);
        return out;
    }
    private void refresh() {
        if (adapter != null) adapter.notifyDataSetChanged();
        if (count != null) count.setText(tr("التطبيقات: ","Apps: ") + filteredApps().size() + "   •   " + tr("المحدد: ","Selected: ") + selected.size());
        if (empty != null) empty.setVisibility(filteredApps().isEmpty() ? View.VISIBLE : View.GONE);
        if (selectAll != null) selectAll.setText(tr("تحديد الكل","Select all"));
    }

    private void actions(AppItem app) {
        String[] opts = {tr("استخراج APK","Extract APK"),tr("إلغاء التثبيت","Uninstall"),tr("تحديد / إلغاء التحديد","Select / deselect"),tr("معلومات التطبيق","App info")};
        new AlertDialog.Builder(this).setTitle(app.label).setItems(opts,(d,w) -> {
            if (w==0) exportApps(java.util.Collections.singletonList(app.pkg));
            else if (w==1) uninstallApps(java.util.Collections.singletonList(app.pkg));
            else if (w==2) { if (!selected.add(app.pkg)) selected.remove(app.pkg); refresh(); }
            else { try { Intent i=new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.pkg)); startActivity(i); } catch(Exception e) { toast("تعذر فتح معلومات التطبيق","Could not open app info"); } }
        }).show();
    }

    private void settingsDialog() {
        String[] opts = {tr("تغيير اللغة: العربية / English","Change language: العربية / English"),tr(dark?"إيقاف الوضع الليلي":"تشغيل الوضع الليلي",dark?"Turn off dark mode":"Turn on dark mode")};
        new AlertDialog.Builder(this).setTitle(tr("الإعدادات","Settings")).setItems(opts,(d,w)->{
            if (w==0) { lang=ar()?"en":"ar"; prefs.edit().putString("lang",lang).apply(); colors(); buildUi(); refresh(); }
            else { dark=!dark; prefs.edit().putBoolean("dark",dark).apply(); colors(); buildUi(); refresh(); }
        }).show();
    }

    private void uninstallSelected() { if (!selected.isEmpty()) uninstallApps(new ArrayList<>(selected)); }
    private void uninstallApps(List<String> pkgs) {
        List<String> valid = new ArrayList<>();
        for (String p : pkgs) if (!getPackageName().equals(p)) valid.add(p);
        if (valid.isEmpty()) { toast("ما ممكن تلغي تثبيت التطبيق دا من داخل نفسو","Cannot uninstall this app from itself"); return; }
        new AlertDialog.Builder(this).setTitle(tr("إلغاء التثبيت","Uninstall apps"))
          .setMessage(tr("أندرويد حيطلب تأكيد لكل تطبيق على حدة. العدد: ","Android asks you to confirm each app. Count: ")+valid.size())
          .setNegativeButton(tr("إلغاء","Cancel"),null)
          .setPositiveButton(tr("متابعة","Continue"),(d,w)->{
              for (String p: valid) { try { startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+p))); } catch(Exception ignored) {} }
          }).show();
    }

    private void exportSelected() { if (!selected.isEmpty()) exportApps(new ArrayList<>(selected)); }
    private void exportApps(List<String> pkgs) {
        new Thread(() -> {
            int ok=0, failed=0;
            for (String pkg:pkgs) {
                try {
                    ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0);
                    List<String> paths=new ArrayList<>();
                    if (ai.sourceDir!=null) paths.add(ai.sourceDir);
                    if (ai.splitSourceDirs!=null) for (String p:ai.splitSourceDirs) if (p!=null) paths.add(p);
                    String label=String.valueOf(getPackageManager().getApplicationLabel(ai)).replaceAll("[\\\\/:*?\"<>|]","_").replaceAll("\\s+","_");
                    for (int i=0;i<paths.size();i++) {
                        String suffix=paths.size()==1?"":"_part"+(i+1);
                        String filename=label+suffix+".apk";
                        saveApk(paths.get(i),filename);
                    }
                    ok++;
                } catch(Exception e) { failed++; }
            }
            final int success=ok, errors=failed;
            runOnUiThread(()->toast(tr("اكتمل الاستخراج: ","Export complete: ")+success+tr(" تطبيقات، فشل "," apps, failed ")+errors,tr("اكتمل الاستخراج: ","Export complete: ")+success+tr(" تطبيقات، فشل "," apps, failed ")+errors));
        }).start();
    }

    private void saveApk(String source,String name) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv=new ContentValues(); cv.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE,"application/vnd.android.package-archive");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/App Extractor");
            cv.put(MediaStore.MediaColumns.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
            if (uri==null) throw new Exception("Unable to create file");
            boolean success=false;
            try (InputStream in=new FileInputStream(source); OutputStream out=getContentResolver().openOutputStream(uri)) {
                if (out==null) throw new Exception("Unable to open file");
                byte[] buf=new byte[65536]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); out.flush(); success=true;
            } finally {
                if(success) { ContentValues done=new ContentValues(); done.put(MediaStore.MediaColumns.IS_PENDING,0); getContentResolver().update(uri,done,null,null); }
                else getContentResolver().delete(uri,null,null);
            }
        } else {
            File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"App Extractor");
            if(!dir.exists()&&!dir.mkdirs()) throw new Exception("Cannot create folder");
            try(InputStream in=new FileInputStream(source);OutputStream out=new java.io.FileOutputStream(new File(dir,name))) {
                byte[] buf=new byte[65536]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
            }
        }
    }

    private void toast(String a,String e) { Toast.makeText(this,tr(a,e),Toast.LENGTH_LONG).show(); }

    private class AppAdapter extends BaseAdapter {
        public int getCount(){return filteredApps().size();}
        public AppItem getItem(int p){return filteredApps().get(p);}
        public long getItemId(int p){return getItem(p).pkg.hashCode();}
        public View getView(int pos,View cv,ViewGroup parent) {
            AppItem app=getItem(pos);
            LinearLayout tile;
            if(cv instanceof LinearLayout) tile=(LinearLayout)cv;
            else {
                tile=new LinearLayout(MainActivity.this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(8),dp(10),dp(8),dp(8));
                ImageView icon=new ImageView(MainActivity.this); icon.setTag("icon"); tile.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
                TextView label=new TextView(MainActivity.this); label.setTag("label"); label.setTextSize(12); label.setTypeface(null,Typeface.BOLD); label.setGravity(Gravity.CENTER); label.setMaxLines(2);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(38)); lp.topMargin=dp(6); tile.addView(label,lp);
                TextView mark=new TextView(MainActivity.this); mark.setTag("mark"); mark.setTextSize(13); mark.setGravity(Gravity.CENTER); tile.addView(mark,new LinearLayout.LayoutParams(-1,dp(24)));
            }
            ImageView icon=(ImageView)tile.findViewWithTag("icon"); TextView label=(TextView)tile.findViewWithTag("label"); TextView mark=(TextView)tile.findViewWithTag("mark");
            icon.setImageDrawable(app.icon); label.setText(app.label); label.setTextColor(fg);
            boolean is=selected.contains(app.pkg); mark.setText(is?"✓  "+tr("محدد","Selected"):"○");
            mark.setTextColor(is?accent:muted);
            tile.setBackground(shape(is?(dark?Color.rgb(32,52,87):Color.rgb(233,241,255)):card,16,is?accent:stroke));
            mark.setOnClickListener(v->{if(!selected.add(app.pkg))selected.remove(app.pkg);refresh();});
            tile.setOnLongClickListener(v->{if(!selected.add(app.pkg))selected.remove(app.pkg);refresh();return true;});
            return tile;
        }
    }
    private static class AppItem {
        final String pkg,label; final Drawable icon;
        AppItem(String p,String l,Drawable i){pkg=p;label=l;icon=i;}
    }
}
