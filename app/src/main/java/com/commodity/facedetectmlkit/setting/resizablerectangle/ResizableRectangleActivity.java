/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.commodity.facedetectmlkit.setting.resizablerectangle;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.commodity.facedetectmlkit.DetectorActivity;
import com.commodity.facedetectmlkit.LegacyCameraConnectionFragment;
import com.commodity.facedetectmlkit.R;
import com.commodity.facedetectmlkit.setting.SettingCameraConnectionFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import static com.commodity.facedetectmlkit.CameraActivity.useFacing;

public class ResizableRectangleActivity extends Activity implements
        Camera.PreviewCallback{

    private FloatingActionButton btnadd;
    private DrawView drawView1;
    private LinearLayout layrect;
    private TextView txt_msg;
    private ImageView imagemsg;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080);

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.resizable_rectangle_activity);

          int facing = (useFacing == CameraCharacteristics.LENS_FACING_BACK) ?
                Camera.CameraInfo.CAMERA_FACING_BACK :
                Camera.CameraInfo.CAMERA_FACING_FRONT;
        SettingCameraConnectionFragment frag = new SettingCameraConnectionFragment(this,
                getLayoutId(),
                DESIRED_PREVIEW_SIZE, facing);
        getFragmentManager().beginTransaction().replace(R.id.rectcontainer, frag).commit();


	}
    public int getLayoutId() {
        return R.layout.fragment_setting_rectangle;
    }
    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

    }
}
