package me.synology.mmyu.umastat;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;



public class MalddalService extends Service {
    TessBaseAPI tessBaseAPI;
    ArrayList<ScriptAndSpecs> scriptAndSpecs;
    ArrayList<String> scriptdata;
    ArrayList<String> specdata;
    ArrayList<Integer> iter1;
    ArrayList<Integer> iter2;
    ArrayList<SkillAndSpecs> skillAndSpecs;
    ArrayList<String> skill_name;
    ArrayList<String> skill_info;
    MediaProjection mediaProjection = null;
    MediaProjectionManager mediaProjectionManager = null;
    WindowManager windowManager;
    View view;
    View view2;
    Configuration configuration;

    boolean view2_displaying = false;

    static final int DATA_JP = 0;
    static final int DATA_KO = 1;
    static final int DATA_EN = 2;

    static final String EXTRA_RESULT_CODE = "resultCode";
    static final String EXTRA_RESULT_INTENT = "resultIntent";
    static final String EXTRA_DATA_LANG = "dataLang";
    static final String EXTRA_PARENT_INTENT = "parentIntent";
    private int resultCode;
    private Intent resultData;
    private int dataLang;
    private Intent parentIntent;

    ImageView stop_button;
    ImageView search_button;
    ArrayList<TextView> scripts = null;
    ArrayList<TextView> specs = null;
    boolean event_visibility = false;

    private static final String CHANNEL_MALDDAL = "channel_malddal";
    private static final int NOTIFY_ID = 9999;

    long button_click_int = 0;

    static final String ACTION_SHUTDOWN = BuildConfig.APPLICATION_ID + ".SHUTDOWN";

    ConstraintLayout service_cl;



    public MalddalService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();

        configuration = getResources().getConfiguration();
        if (mediaProjectionManager == null)
            mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        tessBaseAPI = new TessBaseAPI();
        Toast.makeText(this, "SERVICE NOW RUNNING...", Toast.LENGTH_LONG).show();

        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        view = layoutInflater.inflate(R.layout.service_malddal, null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        windowManager.addView(view, params);

        LayoutInflater layoutInflater2 = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view2 = layoutInflater2.inflate(R.layout.delete_layout, null);
        WindowManager.LayoutParams params2 = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        service_cl = view.findViewById(R.id.service_cl);
        service_cl.setOnTouchListener(new View.OnTouchListener() {
            boolean moved = false;
            float downRawX, downRawY, dX, dY;
            final static float CLICK_DRAG_TOLERANCE = 10;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dX = view.getX() - downRawX;
                        dY = view.getY() - downRawY;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        moved = true;

                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;

                        //params.x = (int)newX;
                        //params.y = (int)newY;
                        windowManager.updateViewLayout(view, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float upRawX = event.getRawX();
                        float upRawY = event.getRawY();

                        float upDX = upRawX - downRawX;
                        float upDY = upRawY - downRawY;

                        if (Math.abs(upDX) < CLICK_DRAG_TOLERANCE && Math.abs(upDY) < CLICK_DRAG_TOLERANCE) {
                            return false;
                        }
                        break;
                }
                return false;
            }
        });


        scripts = new ArrayList<TextView>();
        scripts.add(view.findViewById(R.id.script1));
        scripts.add(view.findViewById(R.id.script2));
        scripts.add(view.findViewById(R.id.script3));
        scripts.add(view.findViewById(R.id.script4));
        scripts.add(view.findViewById(R.id.script5));
        specs = new ArrayList<TextView>();
        specs.add(view.findViewById(R.id.spec1));
        specs.add(view.findViewById(R.id.spec2));
        specs.add(view.findViewById(R.id.spec3));
        specs.add(view.findViewById(R.id.spec4));
        specs.add(view.findViewById(R.id.spec5));



        for(int i = 0; i < 5; i++) {
            specs.get(i).setOnClickListener(new specsListOnClickListener(specs.get(i)) {
                @Override
                public void onClick(View v) {
                    Log.i("data", "skill onClick " + this.spec.getText().toString());
                    String skill_in_script = this.spec.getText().toString();
                    int startIndex = skill_in_script.indexOf("???") + 1;
                    int endIndex = skill_in_script.indexOf("???");
                    if ((skill_in_script.indexOf("???") != -1) && (endIndex != -1)){
                        skill_in_script = skill_in_script.substring(startIndex, endIndex);
                        int skill_index = skill_name.indexOf(skill_in_script);
                        if(skill_index == -1) return;
                        Toast.makeText(getApplicationContext(),
                                skill_in_script + " : " + skill_info.get(skill_index),
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
        stop_button = view2.findViewById(R.id.stop_service);

        search_button = view.findViewById(R.id.search_button);
        search_button.setOnTouchListener(new View.OnTouchListener() {
            boolean moved = false;
            float downRawX, downRawY, dX, dY;
            final static float CLICK_DRAG_TOLERANCE = 10;
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                float stop_locX = stop_button.getX();
                float stop_locY = stop_button.getY();
                int stop_width = stop_button.getWidth() + (int)stop_locX;
                int stop_height = stop_button.getHeight() + (int)stop_locY;
                float upRawX;
                float upRawY;
                float upDX;
                float upDY;

                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dX = params.x - downRawX;
                        dY = params.y - downRawY;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        moved = true;
                        float newX = event.getRawX() + dX;
                        float newY = event.getRawY() + dY;
                        params.x = (int)newX;
                        params.y = (int)newY;
                        upRawX = event.getRawX();
                        upRawY = event.getRawY();

                        upDX = upRawX - downRawX;
                        upDY = upRawY - downRawY;

                        if (Math.abs(upDX) < CLICK_DRAG_TOLERANCE && Math.abs(upDY) < CLICK_DRAG_TOLERANCE) {
                            if (!view2_displaying) {
                                view2_displaying = true;
                                windowManager.addView(view2, params2);
                            }
                        }

                        if (((stop_locX <= event.getRawX()) && (event.getRawX() < stop_width)) &&
                                ((stop_locY <= event.getRawY() - 80) && (event.getRawY() - 80 < stop_height))) {
                            stop_button.setImageResource(R.drawable.remove_red);
                        } else {
                            stop_button.setImageResource(R.drawable.remove_black);
                        }
                        windowManager.updateViewLayout(view, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        upRawX = event.getRawX();
                        upRawY = event.getRawY();

                        upDX = upRawX - downRawX;
                        upDY = upRawY - downRawY;

                        if (Math.abs(upDX) < CLICK_DRAG_TOLERANCE && Math.abs(upDY) < CLICK_DRAG_TOLERANCE) {
                            if (!event_visibility) {
                                event_visibility = true;
                                startCapture();
                            }
                            else {
                                event_visibility = false;
                                for (int i = 0; i < 5; i++) {
                                    scripts.get(i).setVisibility(View.GONE);
                                    specs.get(i).setVisibility(View.GONE);
                                }
                            }
                        }

                        Log.i("touch", "X : " + stop_locX + " Y : " + stop_locY);
                        Log.i("touch", "width : " + stop_width + " height : " + stop_height);
                        Log.i("touch", "locX : " + event.getRawX() + " locY : " + event.getRawY());
                        if (((stop_locX <= event.getRawX()) && (event.getRawX() < stop_width)) &&
                                ((stop_locY <= event.getRawY() - 80) && (event.getRawY() - 80 < stop_height))) {
                            stopSelf();
                        }
                        if(view2_displaying){
                            view2_displaying = false;
                            windowManager.removeView(view2);
                        }
                        return true;
                }
                return false;
            }
        });
        setNightMode();

        String packageName = "jp.co.cygames.umamusume";

        try{
            Intent i = getPackageManager().getLaunchIntentForPackage(packageName);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        catch (Exception e){
            try {
                String url = "market://details?id=" + packageName;
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
            catch (Exception e2) {
                Toast.makeText(getApplicationContext(), "There is no PlayStore either Game", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        if(windowManager != null){
            if(view != null) windowManager.removeView(view);
            Toast.makeText(getApplicationContext(), "SERVICE STOPPED", Toast.LENGTH_LONG).show();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() == null) {
            resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 1337);
            resultData = intent.getParcelableExtra(EXTRA_RESULT_INTENT);
            dataLang = intent.getIntExtra(EXTRA_DATA_LANG, 0);
            parentIntent = intent.getParcelableExtra(EXTRA_PARENT_INTENT);

            String dir = getFilesDir() + "/umapyoi";
            if(checkLanguageFile(dir + "/tessdata")) {
                if(!tessBaseAPI.init(dir, "jpn"))
                    Log.i("data", "tessinit err");

            }
            readEventDataFile(dir);
            readSkillDataFile(dir);
            /*
            boolean event_data_exist = checkEventDataFile(dir + "/xls");
            boolean skill_data_exist = checkSkillDataFile(dir + "/xls");
            if(event_data_exist && skill_data_exist) {
                // Toast.makeText(this, "XLS DATA READ COMPLETED", Toast.LENGTH_LONG).show();
            }

            */

            NotificationManager notificationManager =
                    (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    notificationManager.getNotificationChannel(CHANNEL_MALDDAL) == null) {
                notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL_MALDDAL,
                        "malddal", NotificationManager.IMPORTANCE_DEFAULT));
            }

            NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_MALDDAL);
            b.setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL);
            b.setContentTitle(getString(R.string.service_running))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker(getString(R.string.service_running));
            b.addAction(R.drawable.ic_eject_white_24dp,
                    getString(R.string.notify_shutdown),
                    buildPendingIntent(ACTION_SHUTDOWN));
            startForeground(NOTIFY_ID, b.build());
        } else if (ACTION_SHUTDOWN.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
        }
        return(START_NOT_STICKY);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setNightMode();
    }

    private Point getPointOfView(View view) {
        int[] location = new int[2];
        view.getLocationInWindow(location);
        return new Point(location[0], location[1]);
    }

    boolean checkLanguageFile(String dir){
        String filePath = dir + "/jpn.traineddata";
        File file = new File(dir);
        if (!file.exists() && file.mkdirs())
            createTrainedFiles(dir);
        else if (file.exists()){
            File langDataFile = new File(filePath);
            if (!langDataFile.exists())
                createTrainedFiles(dir);
        }
        return true;
    }

    private void createTrainedFiles(String dir) {
        AssetManager assetMgr = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try{
            inputStream = assetMgr.open("tessdata/jpn.traineddata");
            String destFile = dir + "/jpn.traineddata";
            outputStream = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            outputStream.flush();
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<String> OCR(Bitmap bitmap) {

        Bitmap bitmap2 = Bitmap.createBitmap(bitmap,
                (int)(bitmap.getWidth()*0.1), (int)(bitmap.getHeight()*0.3),
                (int)(bitmap.getWidth()*0.8), (int)(bitmap.getHeight()*0.4));
        //saveBitmapToJpeg(bitmap2, "test");

        tessBaseAPI.setImage(bitmap2);
        String OCRResult = tessBaseAPI.getUTF8Text();
        OCRResult = OCRResult.replaceAll(" ", "");
        String[] tokenized = OCRResult.split("\n");
        ArrayList<String> result = new ArrayList(Arrays.asList(tokenized));
        return result;
    }

    public void readEventDataFile(String dir){
        scriptAndSpecs = new ArrayList<ScriptAndSpecs>();
        try {
            AssetManager assetMgr = this.getAssets();
            InputStream is;
            switch (dataLang) {
                case DATA_KO:
                    is = assetMgr.open("xls/character_script_spec_ko.xls");
                    break;
                case DATA_EN:
                    is = assetMgr.open("xls/character_script_spec_en.xls");
                    break;
                default:
                    is = assetMgr.open("xls/character_script_spec.xls");
                    break;
            }
            Workbook wb = Workbook.getWorkbook(is);
            if(wb != null){
                Sheet sheet = wb.getSheet(0);
                if(sheet != null){
                    int colTotal = sheet.getColumns();
                    int rowIndexStart = 1;
                    int rowTotal = sheet.getColumn(colTotal - 1).length;
                    StringBuilder sb;
                    for(int row = rowIndexStart; row<rowTotal; row++){
                        if (row == 0) continue;
                        ScriptAndSpecs temp = new ScriptAndSpecs();
                        for (int col = 0; col < colTotal; col++){
                            String contents = sheet.getCell(col, row).getContents();
                            switch(col) {
                                case 0:
                                    temp.setId(Integer.parseInt(contents));
                                    break;
                                case 1:
                                    temp.setScript(contents);
                                    break;
                                case 2:
                                    temp.setSpec(contents);
                                    break;
                                case 3:
                                    temp.setIter(Integer.parseInt(contents));
                                    break;
                                case 4:
                                    temp.setIter2(Integer.parseInt(contents));
                                    break;
                                case 5:
                                    temp.setChar_name(contents);
                                    break;
                            }
                        }
                        scriptAndSpecs.add(temp);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (BiffException e) {
            e.printStackTrace();
        }
        scriptdata = new ArrayList<String>();
        specdata = new ArrayList<String>();
        iter1 = new ArrayList<Integer>();
        iter2 = new ArrayList<Integer>();
        for (int i = 0; i < scriptAndSpecs.size(); i++) {
            scriptdata.add(scriptAndSpecs.get(i).getScript());
            specdata.add(scriptAndSpecs.get(i).getSpec());
            iter1.add(scriptAndSpecs.get(i).getIter());
            iter2.add(scriptAndSpecs.get(i).getIter2());
        }
    }

    public void readSkillDataFile(String dir){
        skillAndSpecs = new ArrayList<SkillAndSpecs>();
        try {
            AssetManager assetMgr = this.getAssets();
            InputStream is;
            switch (dataLang) {
                case DATA_KO:
                    is = assetMgr.open("xls/skill_spec_ko.xls");
                    break;
                case DATA_EN:
                    is = assetMgr.open("xls/skill_spec_en.xls");
                    break;
                default:
                    is = assetMgr.open("xls/skill_spec.xls");
            }
            Workbook wb = Workbook.getWorkbook(is);
            if(wb != null){
                Sheet sheet = wb.getSheet(0);
                if(sheet != null){
                    int colTotal = sheet.getColumns();
                    int rowIndexStart = 1;
                    int rowTotal = sheet.getColumn(colTotal - 1).length;
                    StringBuilder sb;
                    for(int row = rowIndexStart; row<rowTotal; row++){
                        if (row == 0) continue;
                        SkillAndSpecs temp = new SkillAndSpecs();
                        for (int col = 0; col < colTotal; col++){
                            String contents = sheet.getCell(col, row).getContents();
                            switch(col) {
                                case 0:
                                    temp.setId(Integer.parseInt(contents));
                                    break;
                                case 1:
                                    temp.setSkill(contents);
                                    break;
                                case 2:
                                    temp.setSkill_info(contents);
                                    break;
                            }
                        }
                        skillAndSpecs.add(temp);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (BiffException e) {
            e.printStackTrace();
        }
        skill_name = new ArrayList<String>();
        skill_info = new ArrayList<String>();
        for (int i = 0; i < skillAndSpecs.size(); i++) {
            skill_name.add(skillAndSpecs.get(i).getSkill());
            skill_info.add(skillAndSpecs.get(i).getSkill_info());
        }
    }

    private void startCapture() {
        if (mediaProjection == null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);
        }
        MediaProjection.Callback cb = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
            }
        };
        WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        final DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        Point size = new Point();
        display.getRealSize(size);
        final int mWidth = size.x;
        final int mHeight = size.y;
        int mDensity = metrics.densityDpi;

        @SuppressLint("WrongConstant") final ImageReader mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);

        final Handler handler = new Handler();

        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        mediaProjection.createVirtualDisplay("malddal", mWidth, mHeight, mDensity, flags, mImageReader.getSurface(), null, handler);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                reader.setOnImageAvailableListener(null, handler);

                Image image = reader.acquireLatestImage();
                if ((image != null) && (mediaProjection != null)) {
                    Image.Plane[] planes = image.getPlanes();
                    if (planes[0].getBuffer() == null) {
                        reader.close();
                        mediaProjection.stop();
                        mediaProjection = null;
                        return;
                    }
                    ByteBuffer buffer = planes[0].getBuffer();

                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * metrics.widthPixels;
                    // create bitmap
                    Bitmap bmp = Bitmap.createBitmap(metrics.widthPixels + (int) ((float) rowPadding / (float) pixelStride), metrics.heightPixels, Bitmap.Config.ARGB_8888);
                    bmp.copyPixelsFromBuffer(buffer);

                    image.close();
                    reader.close();
                    mediaProjection.stop();
                    mediaProjection = null;

                    Bitmap realSizeBitmap = Bitmap.createBitmap(bmp, 0, 0, metrics.widthPixels, bmp.getHeight());
                    bmp.recycle();

                    ArrayList<String> data = OCR(realSizeBitmap);
                    realSizeBitmap.recycle();
                    int index = 0;
                    int min = 99999;
                    boolean found = false;
                    for (int i = 0; i < data.size(); i++) {
                        if(found) break;
                        if (data.get(i).length() >= 3) {
                            String sc = data.get(i).replace("/", "???");
                            sc = sc.replace("7", "???");
                            for (int j = 0; j < scriptAndSpecs.size(); j++) {
                                if (found) break;
                                int leven_dist = leven(scriptdata.get(j), sc);
                                if (leven_dist < min) {
                                    if (leven_dist != scriptdata.get(j).length()) {
                                        if (Math.abs(sc.length() - scriptdata.get(j).length()) <= 3) {
                                            index = j;
                                            min = leven_dist;
                                            if (min == 0) {
                                                found = true;
                                            }
                                        }
                                    }
                                }
                            }
                            Log.i("data", "data index -> " + index);
                            Log.i("data", "reven min -> " + min);
                            Log.i("data", sc);
                        }
                    }
                    ArrayList<String> scriptForPrint = new ArrayList<String>();
                    ArrayList<String> specsForPrint = new ArrayList<String>();
                    while(iter2.get(index) != 1) {
                        index = index - 1;
                    }

                    scriptForPrint.add(scriptdata.get(index));
                    specsForPrint.add(specdata.get(index));
                    index = index + 1;
                    while (iter2.get(index) != 1){
                        scriptForPrint.add(scriptdata.get(index));
                        specsForPrint.add(specdata.get(index));
                        index = index + 1;
                    }
                    for(int i = 0; i < 5; i++) {
                        if (scriptForPrint.size() > i) {
                            scripts.get(i).setVisibility(View.VISIBLE);
                            scripts.get(i).setText(scriptForPrint.get(i));
                            specs.get(i).setVisibility(View.VISIBLE);
                            specs.get(i).setText(specsForPrint.get(i).replace("&", "\n"));
                        } else {
                            scripts.get(i).setText("N/A");
                            scripts.get(i).setVisibility(View.GONE);
                            specs.get(i).setText("N/A");
                            specs.get(i).setVisibility(View.GONE);
                        }
                    }
                }
                else {
                    Toast.makeText(getApplicationContext(), "ERR occurred PLZ RETRY", Toast.LENGTH_LONG).show();
                    image.close();
                    reader.close();
                    try{
                        mediaProjection.stop();
                    }
                    catch(Exception e){
                        Toast.makeText(getApplicationContext(), "ERR plz retry", Toast.LENGTH_LONG).show();
                    }
                    mediaProjection = null;
                }
            }
        }, handler);
    }

    public int leven(String aText, String bText) {
        int aLen = aText.length() + 1;
        int bLen = bText.length() + 1;
        ArrayList<ArrayList<Integer>> array = new ArrayList<ArrayList<Integer>>(aLen);
        for (int i = 0; i < aLen; i++) {
            ArrayList<Integer> temp = new ArrayList<Integer>(bLen);
            array.add(i, temp);
        }
        for (int i = 0; i < aLen; i++) {
            array.get(i).add(0);
        }
        for (int i = 0; i < bLen; i++) {
            array.get(0).add(0);
        }
        int cost = 0;
        for (int i = 1; i < aLen; i++) {
            for (int j = 1; j < bLen; j++) {
                if (aText.charAt(i - 1) != bText.charAt(j - 1)) {
                    cost = 1;
                } else {
                    cost = 0;
                }
                int addNum = array.get(i - 1).get(j) + 1;
                int minusNum = array.get(i).get(j - 1) + 1;
                int modiNum = array.get(i - 1).get(j - 1) + cost;
                int minNum = Math.min(addNum, minusNum);
                minNum = Math.min(modiNum, minNum);
                array.get(i).add(minNum);
            }
        }
        return array.get(aLen - 1).get(bLen - 1);
    }
    private PendingIntent buildPendingIntent(String action) {
        Intent i=new Intent(this, getClass());

        i.setAction(action);

        return(PendingIntent.getService(this, 0, i, 0));
    }

    private class specsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return specs.size();
        }

        @Override
        public Object getItem(int position) {
            return specs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return null;
        }
    }

    public abstract class specsListOnClickListener implements View.OnClickListener {

        protected TextView spec;

        public specsListOnClickListener(TextView spec) {
            this.spec = spec;
        }
    }

    public void setNightMode() {
        int currentNightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                view.findViewById(R.id.service_cl).setBackground(
                        getResources().getDrawable(R.drawable.rounded_background_white,
                                null));
                for (int i = 0; i < 5; i++) {
                    scripts.get(i).setTextColor(getColor(R.color.black));
                    specs.get(i).setTextColor(getColor(R.color.black));
                }
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                view.findViewById(R.id.service_cl).setBackground(
                        getResources().getDrawable(R.drawable.rounded_background_black,
                                null));
                for (int i = 0; i < 5; i++) {
                    scripts.get(i).setTextColor(getColor(R.color.white));
                    specs.get(i).setTextColor(getColor(R.color.white));
                }
                break;
        }
    }

    private void saveBitmapToJpeg(Bitmap bitmap, String name) {

        //??????????????? ?????? ????????? ???????????????.
        File storage = getCacheDir();
        Log.i("data", getFilesDir().toString());

        //????????? ?????? ??????
        String fileName = name + ".jpg";

        //storage ??? ?????? ??????????????? ???????????????.
        File tempFile = new File(storage, fileName);

        try {

            // ???????????? ??? ????????? ???????????????.
            tempFile.createNewFile();

            // ????????? ??? ??? ?????? ???????????? ???????????????.
            FileOutputStream out = new FileOutputStream(tempFile);

            // compress ????????? ????????? ???????????? ???????????? ???????????????.
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            // ????????? ????????? ???????????????.
            out.close();

        } catch (FileNotFoundException e) {
            Log.e("MyTag", "FileNotFoundException : " + e.getMessage());
        } catch (IOException e) {
            Log.e("MyTag", "IOException : " + e.getMessage());
        }
    }


}