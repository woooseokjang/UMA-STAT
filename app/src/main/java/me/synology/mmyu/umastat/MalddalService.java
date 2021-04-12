package me.synology.mmyu.umastat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

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
    MediaProjection mediaProjection;
    MediaProjectionManager mediaProjectionManager;
    WindowManager windowManager;
    View view;

    static final String EXTRA_RESULT_CODE = "resultCode";
    static final String EXTRA_RESULT_INTENT = "resultIntent";
    static final String EXTRA_IN_KOREAN = "inKorean";
    private int resultCode;
    private Intent resultData;
    private boolean inKorean;

    Button search_button = null;
    Button move_service_button = null;
    ArrayList<TextView> scripts = null;
    ArrayList<TextView> specs = null;
    boolean event_visibility = false;

    private static final String CHANNEL_MALDDAL = "channel_malddal";
    private static final int NOTIFY_ID = 9999;




    public MalddalService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        tessBaseAPI = new TessBaseAPI();
        Toast.makeText(this, "SERVICE NOW RUNNING...", Toast.LENGTH_LONG).show();

        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = layoutInflater.inflate(R.layout.service_malddal, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.LEFT;

        windowManager.addView(view, params);
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
            scripts.get(i).setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            specs.get(i).setOnTouchListener(new View.OnTouchListener(){
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
        }

        search_button = view.findViewById(R.id.search_button);
        search_button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (!event_visibility) {
                    event_visibility = true;
                    search_button.setText("찾는중");
                    startCapture();
                    search_button.setText("숨기기");
                    for (int i = 0; i < 5; i++) {
                        scripts.get(i).setVisibility(View.VISIBLE);
                        specs.get(i).setVisibility(View.VISIBLE);
                    }
                }
                else {
                    event_visibility = false;
                    search_button.setText("이벤트 읽기");
                    for (int i = 0; i < 5; i++) {
                        scripts.get(i).setVisibility(View.GONE);
                        specs.get(i).setVisibility(View.GONE);
                    }
                }
            }
        });
        move_service_button = view.findViewById(R.id.move_button);
        move_service_button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE){
                    Point point = getPointOfView(move_service_button);
                    params.x = (int) event.getRawX() - point.x;
                    params.y = (int) event.getRawY() - point.y;
                    windowManager.updateViewLayout(view, params);
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        if(windowManager != null){
            if(view != null) windowManager.removeView(view);
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() == null) {
            resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 1337);
            resultData = intent.getParcelableExtra(EXTRA_RESULT_INTENT);
            inKorean = intent.getBooleanExtra(EXTRA_IN_KOREAN, false);

            String dir = getFilesDir() + "/malddal";
            if(checkLanguageFile(dir + "/tessdata"))
                tessBaseAPI.init(dir, "jpn");
            Toast.makeText(this, "TESS DATA READ COMPLETED", Toast.LENGTH_LONG).show();
            if(checkXLSFile(dir + "/xls")) {
                Toast.makeText(this, "XLS DATA READ COMPLETED", Toast.LENGTH_LONG).show();
            }

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
            b.setContentTitle("malddal")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker("malddal");
            startForeground(NOTIFY_ID, b.build());
        }
        return(START_NOT_STICKY);
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

    boolean checkXLSFile(String dir){
        String filePath;
        if (inKorean) {
            filePath = dir + "/character_script_spec_ko.xls";
        } else {
            filePath = dir + "/character_script_spec.xls";
        }
        File file = new File(dir);
        if (!file.exists() && file.mkdir())
            createXLSFiles(dir);
        else if (file.exists()){
            File langDataFile = new File(filePath);
            if (!langDataFile.exists())
                createXLSFiles(dir);
        }
        scriptAndSpecs = new ArrayList<ScriptAndSpecs>();
        readXLSFile(filePath);
        return true;
    }

    private void createXLSFiles(String dir) {
        AssetManager assetMgr = this.getAssets();
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try{
            String destFile;
            if (inKorean) {
                inputStream = assetMgr.open("xls/character_script_spec_ko.xls");
                destFile = dir + "/character_script_spec_ko.xls";
            } else {
                inputStream = assetMgr.open("xls/character_script_spec.xls");
                destFile = dir + "/character_script_spec.xls";
            }
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

    public Bitmap screenShot(View view) {

        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    public ArrayList<String> OCR(Bitmap bitmap) {
        Bitmap bitmap2 = Bitmap.createBitmap(bitmap,
                (int)(bitmap.getWidth()*0.1), (int)(bitmap.getHeight()*0.5),
                (int)(bitmap.getWidth()*0.8), (int)(bitmap.getHeight()*0.25));
        tessBaseAPI.setImage(bitmap2);
        String OCRResult = tessBaseAPI.getUTF8Text();
        OCRResult = OCRResult.replaceAll(" ", "");
        String[] tokenized = OCRResult.split("\n");
        ArrayList<String> result = new ArrayList(Arrays.asList(tokenized));
        return result;
    }

    public void readXLSFile(String dir){
        try {
            InputStream is = new FileInputStream(new File(dir));
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

    private void startCapture() {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);
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

        final ImageReader mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);

        final Handler handler = new Handler();

        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        mediaProjection.createVirtualDisplay("malddal", mWidth, mHeight, mDensity, flags, mImageReader.getSurface(), null, handler);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                reader.setOnImageAvailableListener(null, handler);

                Image image = reader.acquireLatestImage();

                final Image.Plane[] planes = image.getPlanes();
                final ByteBuffer buffer = planes[0].getBuffer();

                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * metrics.widthPixels;
                // create bitmap
                Bitmap bmp = Bitmap.createBitmap(metrics.widthPixels + (int) ((float) rowPadding / (float) pixelStride), metrics.heightPixels, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(buffer);

                image.close();
                reader.close();

                Bitmap realSizeBitmap = Bitmap.createBitmap(bmp, 0, 0, metrics.widthPixels, bmp.getHeight());
                bmp.recycle();

                ArrayList<String> data = OCR(realSizeBitmap);
                int index = 0;
                boolean found = false;
                for (int i = 0; i < data.size(); i++) {
                    if (data.get(i).length() >= 2) {
                        String sc = data.get(i).replace("/", "!");
                        sc = sc.replace("7", "!");
                        ArrayList<Integer> revenList = new ArrayList<Integer>();
                        for (int j = 0; j < scriptAndSpecs.size(); j++) {
                            int forAppend = leven(scriptdata.get(j), data.get(i));
                            revenList.add(forAppend);
                        }
                        int min = Collections.min(revenList);
                        index = 0;
                        for (int j = 0; j < revenList.size(); j++) {
                            if (revenList.get(j) == min){
                                index = j;
                            }
                        }
                        if (scriptdata.get(index).length() != min) {
                            found = true;
                            break;
                        }

                    }
                }
                ArrayList<String> scriptForPrint = new ArrayList<String>();
                ArrayList<String> specsForPrint = new ArrayList<String>();
                if (found) {
                    scriptForPrint.add(scriptdata.get(index));
                    specsForPrint.add(specdata.get(index));
                    index = index + 1;
                    while (iter2.get(index) != 1){
                        scriptForPrint.add(scriptdata.get(index));
                        specsForPrint.add(specdata.get(index));
                        index = index + 1;
                    }
                }
                for(int i = 0; i < 5; i++) {
                    if (scriptForPrint.size() > i) {
                        scripts.get(i).setText(scriptForPrint.get(i));
                        specs.get(i).setText(specsForPrint.get(i).replace("&", "\n"));
                    } else {
                        scripts.get(i).setText("N/A");
                        specs.get(i).setText("N/A");
                    }
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

}