/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟提醒广播接收器
 * 作用：接收闹钟触发的广播，跳转到提醒弹窗界面
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 接收到闹钟广播时执行
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 将目标组件切换为提醒弹窗 Activity
        intent.setClass(context, AlarmAlertActivity.class);
        // 添加 NEW_TASK 标记（广播接收器启动 Activity 必须加）
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 启动提醒弹窗界面
        context.startActivity(intent);
    }
}