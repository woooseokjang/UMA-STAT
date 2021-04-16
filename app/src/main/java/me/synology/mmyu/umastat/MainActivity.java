package me.synology.mmyu.umastat;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION = 1;
    private static final int PROJECTION_PERMISSION = 2;
    private static final int UPDATE_PERMISSION = 3;

    Activity activity = null;
    Intent intent = null;
    Button start_service = null;
    Button stop_service = null;
    ProgressBar loading_bar = null;
    CheckBox lang_checkBox = null;

    boolean inKorean = false;

    AppUpdateManager appUpdateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = this;
        setContentView(R.layout.activity_main);
        start_service = findViewById(R.id.start_service);
        start_service.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                start_service.setEnabled(false);
                if(checkOverlayPermission()){
                    startService();
                }
                start_service.setEnabled(true);
            }
        });

        stop_service = findViewById(R.id.stop_service);
        stop_service.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop_service.setEnabled(false);
                if (intent != null){
                    stopService(intent);
                    intent = null;
                    Toast.makeText(getApplicationContext(), "SERVICE STOPPED", Toast.LENGTH_LONG).show();
                }
                else{
                    Toast.makeText(getApplicationContext(), "NO SERVICE RUNNING", Toast.LENGTH_LONG).show();
                }

                stop_service.setEnabled(true);
            }
        });
        lang_checkBox = findViewById(R.id.in_korean);
        loading_bar = findViewById(R.id.loading_bar);

        appUpdateManager = AppUpdateManagerFactory.create(getApplicationContext());

        // 업데이트를 체크하는데 사용되는 인텐트를 리턴한다.
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> { // appUpdateManager이 추가되는데 성공하면 발생하는 이벤트
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE // UpdateAvailability.UPDATE_AVAILABLE == 2 이면 앱 true
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) { // 허용된 타입의 앱 업데이트이면 실행 (AppUpdateType.IMMEDIATE || AppUpdateType.FLEXIBLE)
                // 업데이트가 가능하고, 상위 버전 코드의 앱이 존재하면 업데이트를 실행한다.
                requestUpdate(appUpdateInfo);
            }
        });

    }

    @Override
    protected void onDestroy() {
        if(intent != null){
            stopService(intent);
            intent = null;
            Toast.makeText(this, "SERVICE STOPPED", Toast.LENGTH_LONG).show();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode){
            case PROJECTION_PERMISSION:
                if (resultCode==RESULT_OK) {
                    inKorean = lang_checkBox.isChecked();
                    intent = new Intent(this, MalddalService.class)
                            .putExtra(MalddalService.EXTRA_RESULT_CODE, resultCode)
                            .putExtra(MalddalService.EXTRA_RESULT_INTENT, data)
                            .putExtra(MalddalService.EXTRA_IN_KOREAN, inKorean)
                            .putExtra(MalddalService.EXTRA_PARENT_INTENT, getIntent());
                    startService(intent);
                }
            case UPDATE_PERMISSION:
                if (resultCode != RESULT_OK) {
                    Log.d("UPDATE", "Update flow failed! Result code: " + resultCode);
                    Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

                    appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
                        if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                            requestUpdate (appUpdateInfo);
                        }
                    });
                }

        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        appUpdateManager.getAppUpdateInfo().addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                try {
                    appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, UPDATE_PERMISSION);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public boolean checkOverlayPermission(){
        if(!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "오버레이 권한 필요", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return false;
        } else {
            return true;
        }
    }

    public void startService() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            MediaProjectionManager projectionManager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(projectionManager.createScreenCaptureIntent(),
                    PROJECTION_PERMISSION);
        }
    }

    private void requestUpdate (AppUpdateInfo appUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, UPDATE_PERMISSION);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}