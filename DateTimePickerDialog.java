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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框
 * 功能：封装自定义 DateTimePicker，以弹窗形式供用户选择提醒时间
 * 作用：笔记设置闹钟时弹出时间选择框
 */
public class DateTimePickerDialog extends AlertDialog implements DialogInterface.OnClickListener {

    private Calendar mDate = Calendar.getInstance();                // 存储选择的时间
    private boolean mIs24HourView;                                  // 是否24小时制
    private OnDateTimeSetListener mOnDateTimeSetListener;           // 时间设置完成监听器
    private DateTimePicker mDateTimePicker;                         // 自定义时间选择控件

    /**
     * 时间设置完成回调接口
     */
    public interface OnDateTimeSetListener {
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造方法：初始化对话框、时间选择控件、按钮、标题
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);

        // 创建自定义时间选择控件并设为对话框视图
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);

        // 时间改变时更新日历对象与对话框标题
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                                          int dayOfMonth, int hourOfDay, int minute) {
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                updateTitle(mDate.getTimeInMillis());
            }
        });

        // 初始化时间
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0); // 秒数置0，保证时间精度
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        // 设置确认/取消按钮
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);

        // 根据系统设置切换24/12小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));

        // 更新对话框标题（显示当前时间）
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否24小时制
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置时间选择完成监听器
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框标题，显示当前选择的时间
     */
    private void updateTitle(long date) {
        int flag = DateUtils.FORMAT_SHOW_YEAR |
                DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_TIME;

        // 24小时制显示格式
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_12HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 点击确认按钮，回调选择的时间
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}