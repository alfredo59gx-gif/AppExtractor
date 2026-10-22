package com.alfredo.appextractor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private boolean dark;
    private String lang;
    private int bg, panel, tile, fg, muted, accent, stroke, danger;
    private LinearLayout root;
    private EditText search;
    private GridView grid;
    private TextView count;
    private TextView empty;
    private AppAdapter adapter;
    private final List<AppItem> apps = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private String filter = "all";
    private boolean showSystemApps = false;
    private String currentPage = "apps";
    private AppItem currentApp;
    private int lastExportApps = 0;
    private int lastExportFiles = 0;
    private int lastExportErrors = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        dark = prefs.getBoolean("dark", true);
        lang = prefs.getString("lang", "ar");
        showSystemApps = prefs.getBoolean("showSystemApps", false);
        colors();
        loadApps();
        showPage("apps", null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (apps != null && root != null && "apps".equals(currentPage)) loadApps();
    }

    @Override public void onBackPressed() {
        if ("details".equals(currentPage) || "settings".equals(currentPage) || "about".equals(currentPage) ||
            "files".equals(currentPage) || "tools".equals(currentPage) || "complete".equals(currentPage)) {
            showPage("apps", null);
        } else if ("extracting".equals(currentPage)) {
            new AlertDialog.Builder(this).setTitle(tr("جاري الاستخراج","Extraction in progress"))
                .setMessage(tr("عملية الاستخراج مستمرة. الرجوع لن يوقفها.","Extraction is running. Going back will not stop it."))
                .setPositiveButton(tr("متابعة","Continue"), null).show();
        } else {
            super.onBackPressed();
        }
    }

    private boolean ar() { return "ar".equals(lang); }
    private String tr(String a, String e) { return ar() ? a : e; }
    private int dp(float x) { return (int)(x * getResources().getDisplayMetrics().density + .5f); }

    private void colors() {
        bg = dark ? Color.rgb(8,13,22) : Color.rgb(244,247,252);
        panel = dark ? Color.rgb(16,25,39) : Color.WHITE;
        tile = dark ? Color.rgb(21,32,49) : Color.rgb(250,251,254);
        fg = dark ? Color.rgb(241,246,255) : Color.rgb(26,37,55);
        muted = dark ? Color.rgb(151,168,190) : Color.rgb(104,117,137);
        accent = dark ? Color.rgb(44,151,255) : Color.rgb(30,110,240);
        stroke = dark ? Color.rgb(38,57,81) : Color.rgb(224,231,241);
        danger = Color.rgb(239,73,82);
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

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private Button button(String value, boolean primary, boolean destructive) {
        Button b = new Button(this);
        b.setText(value); b.setTextSize(12); b.setAllCaps(false);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : (destructive ? danger : fg));
        b.setBackground(shape(primary ? accent : panel, 13, destructive ? danger : (primary ? accent : stroke)));
        b.setPadding(dp(6), 0, dp(6), 0);
        return b;
    }

    private void showPage(String page, AppItem app) {
        currentPage = page;
        if (app != null) currentApp = app;
        colors();
        root = column();
        root.setPadding(dp(16), dp(10), dp(16), dp(8));
        root.setBackgroundColor(bg);
        root.setLayoutDirection(ar() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        setContentView(root);

        if ("apps".equals(page)) buildAppsPage();
        else if ("details".equals(page)) buildDetailsPage(currentApp);
        else if ("files".equals(page)) buildFilesPage();
        else if ("tools".equals(page)) buildToolsPage();
        else if ("settings".equals(page)) buildSettingsPage();
        else if ("about".equals(page)) buildAboutPage();
        else if ("extracting".equals(page)) buildExtractingPage();
        else if ("complete".equals(page)) buildCompletePage();
    }

    private void header(String title, boolean showSettings) {
        LinearLayout h = row();
        TextView back = text("‹", 31, fg, false);
        back.setGravity(Gravity.CENTER);
        h.addView(back, new LinearLayout.LayoutParams(dp(38), dp(46)));
        back.setOnClickListener(v -> onBackPressed());
        LinearLayout labels = column();
        TextView t = text(title, 20, fg, true);
        labels.addView(t);
        TextView sub = text(tr("استخراج ومشاركة التطبيقات","Export and share installed apps"), 10, muted, false);
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(-2,-2); subp.topMargin=dp(1); labels.addView(sub,subp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2,1); lp.leftMargin=dp(4); lp.rightMargin=dp(4);
        h.addView(labels,lp);
        if (showSettings) {
            TextView set = text("⚙", 23, fg, true);
            set.setGravity(Gravity.CENTER); set.setBackground(shape(panel,13,stroke));
            h.addView(set,new LinearLayout.LayoutParams(dp(46),dp(44)));
            set.setOnClickListener(v->showPage("settings",null));
        }
        root.addView(h);
        addGap(root,8);
    }

    private void addGap(LinearLayout parent, int height) {
        View spacer = new View(this); parent.addView(spacer,new LinearLayout.LayoutParams(1,dp(height)));
    }

    private void buildAppsPage() {
        header("AppExtractor", true);

        LinearLayout searchRow = row();
        search = new EditText(this);
        search.setSingleLine(true); search.setTextSize(14); search.setTextColor(fg);
        search.setHintTextColor(muted); search.setHint(tr("⌕  ابحث عن تطبيق أو اسم الحزمة","⌕  Search app or package"));
        search.setPadding(dp(13),0,dp(13),0); search.setBackground(shape(panel,14,stroke));
        searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(49),1));
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1,-2); srp.bottomMargin=dp(9); root.addView(searchRow,srp);

        TextView listHint=text(tr("تظهر هنا تطبيقات المستخدم القابلة للاستخراج فقط. تطبيقات النظام والتطبيقات التي لا يمكن قراءة ملفاتها مخفية افتراضياً.","Only exportable user apps are shown. System apps and apps whose APK files cannot be read are hidden by default."),10,muted,false);
        listHint.setPadding(dp(3),dp(2),dp(3),dp(8));
        root.addView(listHint);

        count=text("",11,muted,false);
        count.setPadding(dp(2),dp(3),dp(2),dp(8)); root.addView(count);
        FrameLayout holder=new FrameLayout(this);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,0,1); root.addView(holder,hp);
        grid=new GridView(this); grid.setNumColumns(3); grid.setHorizontalSpacing(dp(7)); grid.setVerticalSpacing(dp(8));
        grid.setSelector(android.R.color.transparent); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setVerticalScrollBarEnabled(false);
        holder.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        empty=text(tr("ما لقينا تطبيقات مطابقة","No matching apps found"),13,muted,false); empty.setGravity(Gravity.CENTER); empty.setVisibility(View.GONE);
        holder.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        adapter=new AppAdapter(); grid.setAdapter(adapter);
        grid.setOnItemClickListener((p,v,pos,id)->showPage("details",adapter.getItem(pos)));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int c) { refreshGrid(); }
            public void afterTextChanged(Editable e) {}
        });

        if (!selected.isEmpty()) {
            LinearLayout actions=row(); actions.setPadding(dp(8),dp(7),dp(8),dp(7)); actions.setBackground(shape(panel,15,stroke));
            LinearLayout copy=column();
            copy.addView(text(tr("المحدد: ","Selected: ")+selected.size(),12,fg,true));
            copy.addView(text(tr("اختر إجراءً للتطبيقات","Choose a bulk action"),10,muted,false));
            actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            Button exp=button(tr("استخراج APK","Extract APK"),true,false);
            Button un=button(tr("حذف","Uninstall"),false,true);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(44)); ap.leftMargin=dp(4);
            actions.addView(exp,ap); actions.addView(un,ap);
            exp.setOnClickListener(v->exportApps(new ArrayList<>(selected)));
            un.setOnClickListener(v->uninstallSelected());
            LinearLayout.LayoutParams actp=new LinearLayout.LayoutParams(-1,-2); actp.topMargin=dp(8); root.addView(actions,actp);
        }

        addBottomNav("apps");
        refreshGrid();
    }

    private void addFilter(LinearLayout parent, String key, String label) {
        boolean active=filter.equals(key);
        TextView b=text(label,12,active?Color.WHITE:muted,true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(shape(active?accent:panel,12,active?accent:stroke));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.leftMargin=dp(3); p.rightMargin=dp(3); parent.addView(b,p);
        b.setOnClickListener(v->{filter=key;showPage("apps",null);});
    }

    private void addBottomNav(String active) {
        addGap(root,7);
        LinearLayout nav=row();
        nav.setPadding(dp(3),dp(4),dp(3),dp(4)); nav.setBackground(shape(panel,17,stroke));
        addNavItem(nav,"apps","▦",tr("التطبيقات","Apps"),active);
        addNavItem(nav,"files","▤",tr("ملفات APK","Files"),active);
        addNavItem(nav,"tools","⚒",tr("الأدوات","Tools"),active);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(58)));
    }

    private void addNavItem(LinearLayout nav,String page,String symbol,String label,String active) {
        LinearLayout box=column(); box.setGravity(Gravity.CENTER);
        boolean chosen=page.equals(active);
        TextView icon=text(symbol,18,chosen?accent:muted,true); icon.setGravity(Gravity.CENTER); box.addView(icon);
        TextView tx=text(label,10,chosen?accent:muted,chosen); tx.setGravity(Gravity.CENTER); box.addView(tx);
        box.setBackground(shape(chosen?(dark?Color.rgb(18,45,78):Color.rgb(231,241,255)):Color.TRANSPARENT,12,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.leftMargin=dp(2); p.rightMargin=dp(2); nav.addView(box,p);
        box.setOnClickListener(v->showPage(page,null));
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm=getPackageManager();
        List<PackageInfo> list;
        if (Build.VERSION.SDK_INT>=33) list=pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA));
        else list=pm.getInstalledPackages(PackageManager.GET_META_DATA);
        for (PackageInfo pi:list) {
            try {
                ApplicationInfo ai=pi.applicationInfo;
                if(ai==null) continue;
                String label=String.valueOf(pm.getApplicationLabel(ai));
                Drawable icon=pm.getApplicationIcon(ai);
                boolean system=(ai.flags & ApplicationInfo.FLAG_SYSTEM)!=0;
                String version=pi.versionName==null?"":pi.versionName;
                if (!canReadApkFiles(ai)) continue;
                long size=0;
                try { size=new File(ai.sourceDir).length(); } catch(Exception ignored) {}
                apps.add(new AppItem(pi.packageName,label,icon,system,version,size,ai.sourceDir));
            } catch(Exception ignored) {}
        }
        apps.sort((a,b)->a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT)));
        refreshGrid();
    }

    private boolean canReadApkFiles(ApplicationInfo ai) {
        if (ai == null || ai.sourceDir == null) return false;
        File base = new File(ai.sourceDir);
        if (!base.isFile() || !base.canRead() || base.length() <= 0) return false;
        if (ai.splitSourceDirs != null) {
            for (String splitPath : ai.splitSourceDirs) {
                if (splitPath == null) return false;
                File split = new File(splitPath);
                if (!split.isFile() || !split.canRead() || split.length() <= 0) return false;
            }
        }
        return true;
    }

    private List<AppItem> filteredApps() {
        String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<AppItem> out=new ArrayList<>();
        for(AppItem a:apps) {
            boolean category = showSystemApps || !a.system;
            if(category&&(q.isEmpty()||a.label.toLowerCase(Locale.ROOT).contains(q)||a.pkg.toLowerCase(Locale.ROOT).contains(q))) out.add(a);
        }
        return out;
    }

    private void refreshGrid() {
        if(adapter!=null) adapter.notifyDataSetChanged();
        if(count!=null) count.setText(tr("التطبيقات ","Apps ")+filteredApps().size()+"    •    "+tr("المحدد ","Selected ")+selected.size());
        if(empty!=null) empty.setVisibility(filteredApps().isEmpty()?View.VISIBLE:View.GONE);
    }

    private void buildDetailsPage(AppItem app) {
        if(app==null) { showPage("apps",null); return; }
        header(tr("تفاصيل التطبيق","App details"),false);
        ScrollView scroll=new ScrollView(this); LinearLayout content=column(); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(4),dp(8),dp(4),dp(10));
        ImageView icon=new ImageView(this); icon.setImageDrawable(app.icon);
        content.addView(icon,new LinearLayout.LayoutParams(dp(76),dp(76)));
        TextView name=text(app.label,21,fg,true); name.setGravity(Gravity.CENTER); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(10); content.addView(name,np);
        TextView pkg=text(app.pkg,11,muted,false); pkg.setGravity(Gravity.CENTER); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2); pp.topMargin=dp(4); pp.bottomMargin=dp(13); content.addView(pkg,pp);
        LinearLayout info=column(); info.setPadding(dp(15),dp(12),dp(15),dp(12)); info.setBackground(shape(panel,16,stroke));
        addInfo(info,tr("الإصدار","Version"),app.version);
        addInfo(info,tr("الحجم التقريبي لملف APK","Approximate APK size"),formatBytes(app.size));
        addInfo(info,tr("نوع التطبيق","App type"),app.system?tr("تطبيق نظام","System app"):tr("تطبيق مستخدم","User app"));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2); ip.bottomMargin=dp(18); content.addView(info,ip);
        Button exp=button("↓   "+tr("استخراج APK","Extract APK"),true,false);
        Button share=button("↗   "+tr("مشاركة ملف APK","Share APK file"),false,false);
        Button uninstall=button("▤   "+tr("إلغاء التثبيت","Uninstall"),false,true);
        content.addView(exp,new LinearLayout.LayoutParams(-1,dp(49)));
        LinearLayout.LayoutParams b2=new LinearLayout.LayoutParams(-1,dp(47)); b2.topMargin=dp(8); content.addView(share,b2);
        LinearLayout.LayoutParams b3=new LinearLayout.LayoutParams(-1,dp(47)); b3.topMargin=dp(8); content.addView(uninstall,b3);
        exp.setOnClickListener(v->exportApps(Collections.singletonList(app.pkg)));
        share.setOnClickListener(v->shareExportedFile(app.label));
        uninstall.setOnClickListener(v->uninstallApps(Collections.singletonList(app.pkg)));
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void addInfo(LinearLayout box,String label,String value) {
        LinearLayout r=row(); TextView l=text(label,12,muted,false); TextView v=text(value,12,fg,true); v.setGravity(Gravity.RIGHT);
        r.addView(l,new LinearLayout.LayoutParams(0,-2,1)); r.addView(v,new LinearLayout.LayoutParams(0,-2,1)); box.addView(r);
        addGap(box,10);
    }

    private void uninstallSelected() {
        if(selected.isEmpty()) return;
        uninstallApps(new ArrayList<>(selected));
    }

    private void uninstallApps(List<String> pkgs) {
        List<String> valid=new ArrayList<>();
        for(String p:pkgs) if(!getPackageName().equals(p)) valid.add(p);
        if(valid.isEmpty()) { toast(tr("ما ممكن تلغي تثبيت التطبيق دا من داخل نفسو","Cannot uninstall this app from itself")); return; }
        String first=valid.get(0);
        new AlertDialog.Builder(this).setTitle(tr("تأكيد إلغاء التثبيت","Confirm uninstall"))
            .setMessage(tr("أندرويد حيعرض تأكيد النظام. لو محدد عدة تطبيقات، كرر العملية لكل تطبيق. العدد المحدد: ","Android will show a system confirmation. Repeat for each app if multiple are selected. Selected: ")+valid.size())
            .setNegativeButton(tr("إلغاء","Cancel"),null)
            .setPositiveButton(tr("متابعة","Continue"),(d,w)->{
                try { startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+first))); }
                catch(Exception e) { toast(tr("تعذر فتح شاشة إلغاء التثبيت","Could not open uninstall screen")); }
            }).show();
    }

    private void buildExtractingPage() {
        header(tr("جاري استخراج التطبيق","Extracting APK"),false);
        LinearLayout center=column(); center.setGravity(Gravity.CENTER); center.setPadding(dp(10),dp(20),dp(10),dp(20));
        TextView symbol=text("⬇",58,accent,true); symbol.setGravity(Gravity.CENTER); center.addView(symbol);
        TextView title=text(tr("جاري تجهيز ملفات APK","Preparing APK files"),18,fg,true); title.setGravity(Gravity.CENTER); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(12); center.addView(title,tp);
        TextView desc=text(tr("قد يحتوي التطبيق على ملف أساسي وأجزاء إضافية.","The app may contain a base APK and additional split APKs."),12,muted,false); desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2); dp1.topMargin=dp(8); center.addView(desc,dp1);
        ProgressBar progress=new ProgressBar(this); progress.setIndeterminate(true); LinearLayout.LayoutParams pr=new LinearLayout.LayoutParams(dp(62),dp(62)); pr.topMargin=dp(22); center.addView(progress,pr);
        root.addView(center,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void exportApps(List<String> pkgs) {
        if(pkgs==null||pkgs.isEmpty()) { toast(tr("حدد تطبيق واحد على الأقل","Select at least one app")); return; }
        showPage("extracting",null);
        new Thread(()->{
            int ok=0, failed=0, files=0;
            for(String pkg:pkgs) {
                try {
                    ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0);
                    List<String> paths=new ArrayList<>();
                    if(ai.sourceDir!=null) paths.add(ai.sourceDir);
                    if(ai.splitSourceDirs!=null) for(String p:ai.splitSourceDirs) if(p!=null) paths.add(p);
                    String label=sanitizeFileName(String.valueOf(getPackageManager().getApplicationLabel(ai)));
                    for(int i=0;i<paths.size();i++) {
                        String suffix=paths.size()==1?"":"_part"+(i+1);
                        saveApk(paths.get(i),label+suffix+".apk");
                        files++;
                    }
                    ok++;
                } catch(Exception e) { failed++; }
            }
            lastExportApps=ok; lastExportErrors=failed; lastExportFiles=files;
            runOnUiThread(()->showPage("complete",null));
        }).start();
    }

    private void buildCompletePage() {
        header(tr("اكتمل الاستخراج","Export complete"),false);
        LinearLayout center=column(); center.setGravity(Gravity.CENTER); center.setPadding(dp(8),dp(15),dp(8),dp(15));
        TextView check=text("✓",58,Color.rgb(46,220,135),true); check.setGravity(Gravity.CENTER); check.setBackground(shape(dark?Color.rgb(12,58,49):Color.rgb(225,250,239),60,Color.rgb(46,220,135)));
        center.addView(check,new LinearLayout.LayoutParams(dp(96),dp(96)));
        TextView title=text(tr("تم حفظ ملفات APK","APK files saved"),20,fg,true); title.setGravity(Gravity.CENTER); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(15); center.addView(title,tp);
        TextView details=text(tr("تم استخراج ","Exported ")+lastExportApps+tr(" تطبيقات بنجاح"," apps successfully")+"\n"+tr("عدد الملفات: ","Files: ")+lastExportFiles+"\n"+tr("تعذر استخراج: ","Failed: ")+lastExportErrors,13,muted,false);
        details.setGravity(Gravity.CENTER); LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2); dlp.topMargin=dp(10); dlp.bottomMargin=dp(18); center.addView(details,dlp);
        TextView path=text("Download/App Extractor",13,fg,true); path.setGravity(Gravity.CENTER); path.setPadding(dp(12),dp(15),dp(12),dp(15)); path.setBackground(shape(panel,14,stroke)); center.addView(path,new LinearLayout.LayoutParams(-1,-2));
        Button files=button("▤   "+tr("فتح مدير الملفات","Open APK files"),true,false); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(48)); fp.topMargin=dp(18); center.addView(files,fp);
        Button home=button(tr("العودة للتطبيقات","Back to apps"),false,false); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(47)); bp.topMargin=dp(8); center.addView(home,bp);
        files.setOnClickListener(v->showPage("files",null)); home.setOnClickListener(v->showPage("apps",null));
        root.addView(center,new LinearLayout.LayoutParams(-1,0,1));
    }

    private void buildFilesPage() {
        header(tr("ملفات APK","APK files"),false);
        TextView path=text("Download / App Extractor",12,muted,false); path.setPadding(dp(12),dp(12),dp(12),dp(12)); path.setBackground(shape(panel,12,stroke)); root.addView(path,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this); LinearLayout list=column(); list.setPadding(0,dp(9),0,0); int found=0;
        if(Build.VERSION.SDK_INT>=29) {
            Cursor c=null;
            try {
                String[] proj={MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME,MediaStore.Downloads.SIZE,MediaStore.Downloads.RELATIVE_PATH};
                String selection=MediaStore.Downloads.RELATIVE_PATH+"=?";
                String[] args={Environment.DIRECTORY_DOWNLOADS+"/App Extractor/"};
                c=getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,proj,selection,args,MediaStore.Downloads.DATE_ADDED+" DESC");
                if(c!=null) while(c.moveToNext()) {
                    long id=c.getLong(0); String name=c.getString(1); long size=c.isNull(2)?0:c.getLong(2);
                    Uri uri=Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,String.valueOf(id));
                    addFileRow(list,name,size,uri); found++;
                }
            } catch(Exception ignored) {} finally { if(c!=null)c.close(); }
        } else {
            File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"App Extractor");
            File[] files=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".apk"));
            if(files!=null) for(File f:files) { addFileRow(list,f.getName(),f.length(),Uri.fromFile(f)); found++; }
        }
        if(found==0) {
            TextView none=text(tr("لسه ما في ملفات APK محفوظة هنا.","No APK files saved here yet."),13,muted,false); none.setGravity(Gravity.CENTER); none.setPadding(dp(10),dp(40),dp(10),dp(40)); list.addView(none);
        }
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addBottomNav("files");
    }

    private void addFileRow(LinearLayout list,String name,long size,Uri uri) {
        LinearLayout r=row(); r.setPadding(dp(10),dp(10),dp(10),dp(10)); r.setBackground(shape(panel,13,stroke));
        TextView icon=text("APK",13,accent,true); icon.setGravity(Gravity.CENTER); icon.setBackground(shape(dark?Color.rgb(22,49,80):Color.rgb(230,241,255),10,0)); r.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout meta=column(); TextView n=text(name,12,fg,true); n.setMaxLines(2); TextView s=text(formatBytes(size),10,muted,false); meta.addView(n); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,-2); slp.topMargin=dp(3); meta.addView(s,slp);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-2,1); mp.leftMargin=dp(10); mp.rightMargin=dp(7); r.addView(meta,mp);
        TextView share=text("↗",21,accent,true); share.setGravity(Gravity.CENTER); r.addView(share,new LinearLayout.LayoutParams(dp(35),dp(42)));
        share.setOnClickListener(v->shareUri(uri,name));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dp(7); list.addView(r,rp);
    }

    private void shareExportedFile(String appName) {
        Uri uri=findLatestApk(appName);
        if(uri!=null) { shareUri(uri,appName+".apk"); return; }
        toast(tr("ما لقيت ملف APK محفوظ للمشاركة؛ استخرج التطبيق أولاً.","No saved APK found to share; extract the app first."));
    }

    private Uri findLatestApk(String appName) {
        if(Build.VERSION.SDK_INT<29) return null;
        Cursor c=null;
        try {
            String[] proj={MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME};
            String selection=MediaStore.Downloads.RELATIVE_PATH+"=? AND "+MediaStore.Downloads.DISPLAY_NAME+" LIKE ?";
            String[] args={Environment.DIRECTORY_DOWNLOADS+"/App Extractor/",sanitizeFileName(appName)+"%.apk"};
            c=getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,proj,selection,args,MediaStore.Downloads.DATE_ADDED+" DESC");
            if(c!=null&&c.moveToFirst()) return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,String.valueOf(c.getLong(0)));
        } catch(Exception ignored) {} finally { if(c!=null)c.close(); }
        return null;
    }

    private void shareUri(Uri uri,String name) {
        try {
            Intent send=new Intent(Intent.ACTION_SEND); send.setType("application/vnd.android.package-archive");
            send.putExtra(Intent.EXTRA_STREAM,uri); send.putExtra(Intent.EXTRA_SUBJECT,name);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,tr("مشاركة ملف APK","Share APK file")));
        } catch(Exception e) { toast(tr("تعذر مشاركة الملف","Could not share file")); }
    }

    private void buildToolsPage() {
        header(tr("الأدوات","Tools"),false);
        ScrollView sc=new ScrollView(this); LinearLayout body=column(); body.setPadding(0,dp(3),0,dp(8));
        addTool(body,"▤",tr("مدير ملفات APK","APK file manager"),tr("عرض الملفات المستخرجة ومشاركتها","Browse and share extracted files"),"files");
        addTool(body,"⚙",tr("الإعدادات","Settings"),tr("اللغة والوضع الليلي","Language and dark mode"),"settings");
        addTool(body,"ⓘ",tr("حول التطبيق","About"),tr("معلومات الإصدار والخصوصية","Version and privacy info"),"about");
        sc.addView(body); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); addBottomNav("tools");
    }

    private void addTool(LinearLayout body,String symbol,String title,String subtitle,String page) {
        LinearLayout r=row(); r.setPadding(dp(13),dp(12),dp(13),dp(12)); r.setBackground(shape(panel,15,stroke));
        TextView ico=text(symbol,24,accent,true); ico.setGravity(Gravity.CENTER); r.addView(ico,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout labels=column(); labels.addView(text(title,14,fg,true)); TextView s=text(subtitle,11,muted,false); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(4); labels.addView(s,sp);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.leftMargin=dp(10); r.addView(labels,lp);
        TextView arrow=text("›",27,muted,false); r.addView(arrow);
        r.setOnClickListener(v->showPage(page,null));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dp(8); body.addView(r,rp);
    }

    private void buildSettingsPage() {
        header(tr("الإعدادات","Settings"),false);
        TextView sec=text(tr("المظهر","Appearance"),12,accent,true); sec.setPadding(dp(4),dp(10),dp(4),dp(8)); root.addView(sec);
        addSettingToggle(tr("الوضع الليلي","Dark mode"),tr("واجهة داكنة بألوان زرقاء","Dark interface with blue accents"),dark,()->{
            dark=!dark; prefs.edit().putBoolean("dark",dark).apply(); showPage("settings",null);
        });
        addSettingToggle(tr("اللغة العربية","Arabic language"),tr("التبديل بين العربية والإنجليزية","Switch Arabic and English"),ar(),()->{
            lang=ar()?"en":"ar"; prefs.edit().putString("lang",lang).apply(); showPage("settings",null);
        });
        TextView ex=text(tr("الاستخراج","Extraction"),12,accent,true); ex.setPadding(dp(4),dp(15),dp(4),dp(8)); root.addView(ex);
        addSettingToggle(tr("إظهار تطبيقات النظام","Show system apps"),tr("مخفية افتراضياً؛ فعّل هذا الخيار لعرضها إذا كانت ملفاتها قابلة للقراءة.","Hidden by default; enable to show those with readable APK files."),showSystemApps,()->{
            showSystemApps=!showSystemApps;
            prefs.edit().putBoolean("showSystemApps",showSystemApps).apply();
            showPage("settings",null);
        });
        TextView securityTitle=text(tr("الخصوصية والأمان","Privacy and security"),12,accent,true);
        securityTitle.setPadding(dp(4),dp(15),dp(4),dp(8));
        root.addView(securityTitle);
        TextView securityNote=text(tr("لا يحتاج التطبيق إلى اتصال بالإنترنت. قائمة التطبيقات وملفات APK تبقى على جهازك. بعض تطبيقات النظام أو التطبيقات المقسّمة قد لا تكون قابلة للاستخراج؛ تُخفى التطبيقات التي لا يمكن قراءة ملفاتها.","The app does not need internet access. The app list and exported APKs stay on your device. Some system or split apps may not be exportable; apps whose APK files cannot be read are hidden."),11,muted,false);
        securityNote.setPadding(dp(12),dp(12),dp(12),dp(12));
        securityNote.setBackground(shape(panel,13,stroke));
        LinearLayout.LayoutParams secp=new LinearLayout.LayoutParams(-1,-2); secp.topMargin=dp(3); root.addView(securityNote,secp);
        TextView note=text(tr("تُحفظ الملفات في Download/App Extractor. التطبيقات المقسّمة قد تنتج أكثر من ملف APK، ولا يكفي الملف الأساسي دائماً لإعادة تثبيتها.","Files are saved in Download/App Extractor. Split apps may export multiple APK files; the base file alone may not reinstall the app."),11,muted,false);
        note.setPadding(dp(12),dp(12),dp(12),dp(12)); note.setBackground(shape(panel,13,stroke)); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.topMargin=dp(9); root.addView(note,np);
        View spacer=new View(this); root.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
        addBottomNav("tools");
    }

    private void addSettingToggle(String title,String subtitle,boolean checked,Runnable action) {
        LinearLayout r=row(); r.setPadding(dp(12),dp(10),dp(12),dp(10)); r.setBackground(shape(panel,14,stroke));
        LinearLayout labels=column(); labels.addView(text(title,13,fg,true)); TextView s=text(subtitle,10,muted,false); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(3); labels.addView(s,sp);
        r.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        CheckBox cb=new CheckBox(this); cb.setButtonTintList(android.content.res.ColorStateList.valueOf(checked?accent:muted)); cb.setChecked(checked); r.addView(cb);
        r.setOnClickListener(v->action.run()); cb.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dp(7); root.addView(r,rp);
    }

    private void buildAboutPage() {
        header(tr("حول التطبيق","About"),false);
        LinearLayout c=column(); c.setGravity(Gravity.CENTER); c.setPadding(dp(12),dp(20),dp(12),dp(20));
        TextView logo=text("APK",25,Color.WHITE,true); logo.setGravity(Gravity.CENTER); logo.setBackground(shape(accent,24,0)); c.addView(logo,new LinearLayout.LayoutParams(dp(84),dp(84)));
        TextView title=text("AppExtractor",22,fg,true); title.setGravity(Gravity.CENTER); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(12); c.addView(title,tp);
        TextView version=text(tr("الإصدار 1.0.0","Version 1.0.0"),12,muted,false); version.setGravity(Gravity.CENTER); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2); vp.topMargin=dp(4); c.addView(version,vp);
        TextView desc=text(tr("أداة بسيطة لاستعراض التطبيقات المثبتة واستخراج ملفات APK وحفظها في هاتفك.","A utility to browse installed apps, export APK files, and save them on your device."),13,fg,false); desc.setGravity(Gravity.CENTER); LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2); dp1.topMargin=dp(20); c.addView(desc,dp1);
        TextView privacy=text(tr("لا يرسل التطبيق قائمة تطبيقاتك إلى أي خادم. عملية الاستخراج تتم على الجهاز.","The app does not upload your app list. APK extraction is performed locally on the device."),11,muted,false); privacy.setGravity(Gravity.CENTER); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2); pp.topMargin=dp(16); c.addView(privacy,pp);
        root.addView(c,new LinearLayout.LayoutParams(-1,0,1));
    }

    private class AppAdapter extends BaseAdapter {
        public int getCount(){return filteredApps().size();}
        public AppItem getItem(int p){return filteredApps().get(p);}
        public long getItemId(int p){return getItem(p).pkg.hashCode();}
        public View getView(int pos,View cv,ViewGroup parent) {
            AppItem app=getItem(pos); LinearLayout card;
            if(cv instanceof LinearLayout && "appTile".equals(((LinearLayout)cv).getTag())) card=(LinearLayout)cv;
            else {
                card=column(); card.setTag("appTile"); card.setGravity(Gravity.CENTER); card.setPadding(dp(5),dp(10),dp(5),dp(8));
                FrameLayout iconHolder=new FrameLayout(MainActivity.this);
                ImageView icon=new ImageView(MainActivity.this); icon.setTag("icon");
                FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(47),dp(47),Gravity.CENTER); iconHolder.addView(icon,ip);
                TextView check=text("✓",11,Color.WHITE,true); check.setGravity(Gravity.CENTER); check.setTag("check");
                FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.TOP|Gravity.END); iconHolder.addView(check,cp);
                card.addView(iconHolder,new LinearLayout.LayoutParams(dp(56),dp(54)));
                TextView label=text("",11,fg,true); label.setTag("label"); label.setGravity(Gravity.CENTER); label.setMaxLines(2);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(34)); lp.topMargin=dp(6); card.addView(label,lp);
                TextView category=text("",9,muted,false); category.setTag("category"); category.setGravity(Gravity.CENTER); LinearLayout.LayoutParams cp2=new LinearLayout.LayoutParams(-1,dp(18)); cp2.topMargin=dp(2); card.addView(category,cp2);
            }
            ImageView icon=(ImageView)card.findViewWithTag("icon");
            TextView check=(TextView)card.findViewWithTag("check");
            TextView label=(TextView)card.findViewWithTag("label");
            TextView category=(TextView)card.findViewWithTag("category");
            icon.setImageDrawable(app.icon); label.setText(app.label);
            category.setText(app.system?tr("تطبيق نظام","System app"):tr("تطبيق مستخدم","User app"));
            boolean is=selected.contains(app.pkg);
            check.setVisibility(is?View.VISIBLE:View.GONE); check.setBackground(shape(accent,10,0));
            card.setBackground(shape(is?(dark?Color.rgb(16,47,80):Color.rgb(229,240,255)):tile,15,is?accent:stroke));
            card.setOnClickListener(v->{if(!selected.isEmpty()){toggleSelection(app); } else {showPage("details",app);} });
            card.setOnLongClickListener(v->{toggleSelection(app); return true;});
            check.setOnClickListener(v->toggleSelection(app));
            return card;
        }
    }

    private void toggleSelection(AppItem app) {
        if(!selected.add(app.pkg)) selected.remove(app.pkg);
        refreshGrid();
        showPage("apps",null);
    }

    private void saveApk(String source,String name) throws Exception {
        if(Build.VERSION.SDK_INT>=29) {
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE,"application/vnd.android.package-archive");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/App Extractor");
            cv.put(MediaStore.MediaColumns.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
            if(uri==null) throw new Exception("Unable to create file");
            boolean success=false;
            try(InputStream in=new FileInputStream(source); OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new Exception("Unable to open file");
                byte[] buf=new byte[65536]; int n;
                while((n=in.read(buf))!=-1) out.write(buf,0,n);
                out.flush(); success=true;
            } finally {
                if(success) { ContentValues done=new ContentValues(); done.put(MediaStore.MediaColumns.IS_PENDING,0); getContentResolver().update(uri,done,null,null); }
                else getContentResolver().delete(uri,null,null);
            }
        } else {
            File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"App Extractor");
            if(!dir.exists()&&!dir.mkdirs()) throw new Exception("Cannot create folder");
            try(InputStream in=new FileInputStream(source); OutputStream out=new java.io.FileOutputStream(new File(dir,name))) {
                byte[] buf=new byte[65536]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
            }
        }
    }

    private String sanitizeFileName(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '-' || ch == ' ') {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        return result.toString().trim().replace(' ', '_');
    }

    private String formatBytes(long bytes) {
        if(bytes<1024) return bytes+" B";
        if(bytes<1024*1024) return String.format(Locale.US,"%.1f KB",bytes/1024.0);
        return String.format(Locale.US,"%.1f MB",bytes/(1024.0*1024.0));
    }

    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }

    private static class AppItem {
        final String pkg,label,version,source;
        final Drawable icon;
        final boolean system;
        final long size;
        AppItem(String p,String l,Drawable i,boolean s,String v,long z,String src) {
            pkg=p; label=l; icon=i; system=s; version=v; size=z; source=src;
        }
    }
}
