package me.synology.mmyu.umastat;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION = 1;
    private static final int PROJECTION_PERMISSION = 2;
    private static final int UPDATE_PERMISSION = 3;

    Configuration configuration;

    Activity activity = null;
    Intent intent = null;
    Button start_service = null;
    Button stop_service = null;
    RadioButton rbt_jp, rbt_ko, rbt_en;
    RadioGroup rbt_group;

    int rbt_val;


    AppUpdateManager appUpdateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configuration = getResources().getConfiguration();
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
                }
                else{
                    Toast.makeText(getApplicationContext(), "NO SERVICE RUNNING", Toast.LENGTH_LONG).show();
                }

                stop_service.setEnabled(true);
            }
        });

        rbt_jp = findViewById(R.id.jp);
        rbt_ko = findViewById(R.id.ko);
        rbt_en = findViewById(R.id.en);

        rbt_group = findViewById(R.id.languages);

        rbt_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.jp:
                        rbt_val = 0;
                        break;
                    case R.id.ko:
                        rbt_val = 1;
                        break;
                    case R.id.en:
                        rbt_val = 2;
                        break;
                }
            }
        });

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

        setNightMode();

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
                if (resultCode == RESULT_OK) {
                    intent = new Intent(this, MalddalService.class)
                            .putExtra(MalddalService.EXTRA_RESULT_CODE, resultCode)
                            .putExtra(MalddalService.EXTRA_RESULT_INTENT, data)
                            .putExtra(MalddalService.EXTRA_DATA_LANG, rbt_val)
                            .putExtra(MalddalService.EXTRA_PARENT_INTENT, getIntent());
                    startService(intent);
                }
                break;
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
                break;
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

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setNightMode();
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

    public void setNightMode() {
        int currentNightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (currentNightMode) {
            case Configuration.UI_MODE_NIGHT_NO:
                findViewById(R.id.languages).setBackground(
                        getResources().getDrawable(R.drawable.rounded_background_white,
                                null));
                break;
            case Configuration.UI_MODE_NIGHT_YES:
                findViewById(R.id.languages).setBackground(
                        getResources().getDrawable(R.drawable.rounded_background_black,
                                null));
                break;
        }
    }



}