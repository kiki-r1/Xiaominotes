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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器
 * 作用：系统开机 / 重启后，重新注册所有未过期的笔记提醒闹钟
 * 原因：Android 系统重启后会清空所有闹钟，必须重新设置
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 查询需要的字段：笔记ID、提醒时间
    private static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE
    };

    // 列索引
    private static final int COLUMN_ID                = 0;
    private static final int COLUMN_ALERTED_DATE      = 1;

    /**
     * 接收广播（开机完成等）后执行
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前时间，只恢复未来的提醒
        long currentDate = System.currentTimeMillis();

        // 查询数据库中：提醒时间 > 当前时间，且类型为笔记的所有数据
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },
                null);

        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    // 获取该笔记的提醒时间
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);

                    // 创建跳转广播：提醒时间到后发送给 AlarmReceiver
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    // 携带笔记ID
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));

                    // 创建延迟广播意图
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);

                    // 获取系统闹钟服务
                    AlarmManager alarmManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);

                    // 设置闹钟：RTC_WAKEUP 表示唤醒CPU执行
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (c.moveToNext());
            }
            c.close();
        }
    }
}