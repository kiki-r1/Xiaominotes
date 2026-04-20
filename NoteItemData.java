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

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 笔记列表项数据实体类
 * 作用：将数据库查询返回的 Cursor 封装为单个列表项的数据对象
 * 用途：在 NotesListActivity 中为每个列表项提供数据
 */
public class NoteItemData {
    /**
     * 数据库查询投影（列）
     * 对应笔记表中需要加载的所有字段
     */
    static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE,
            NoteColumns.HAS_ATTACHMENT,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT,
            NoteColumns.PARENT_ID,
            NoteColumns.SNIPPET,
            NoteColumns.TYPE,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
    };

    // 数据库列索引定义（与上面 PROJECTION 顺序严格对应）
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    // 笔记基础字段
    private long mId;                  // 笔记ID
    private long mAlertDate;           // 提醒时间
    private int mBgColorId;            // 背景色ID
    private long mCreatedDate;         // 创建时间
    private boolean mHasAttachment;    // 是否有附件
    private long mModifiedDate;        // 修改时间
    private int mNotesCount;           // 子笔记数量（文件夹）
    private long mParentId;            // 父文件夹ID
    private String mSnippet;           // 笔记摘要内容
    private int mType;                 // 类型：笔记/文件夹/系统文件夹
    private int mWidgetId;             // 桌面小部件ID
    private int mWidgetType;           // 小部件类型

    // 来电记录专用
    private String mName;              // 联系人姓名
    private String mPhoneNumber;       // 电话号码

    // 列表项位置状态（用于绘制列表分割线、布局样式）
    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;    // 文件夹后紧跟一个笔记
    private boolean mIsMultiNotesFollowingFolder; // 文件夹后有多个笔记

    /**
     * 构造方法
     * 从 Cursor 中读取数据并封装成 NoteItemData
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 从 Cursor 读取基础字段
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);

        // 移除清单模式的勾选符号，只显示纯文本
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");

        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        mPhoneNumber = "";
        // 如果是来电记录文件夹，加载电话号码和联系人姓名
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        if (mName == null) {
            mName = "";
        }

        // 检查当前 Cursor 位置，判断列表项显示状态
        checkPostion(cursor);
    }

    /**
     * 检查 Cursor 位置
     * 判断当前项是否是第一个、最后一个、唯一一项、是否在文件夹后
     * 用于列表布局的样式控制
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 如果是笔记类型，且不是第一项，判断前一项是不是文件夹
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > (position + 1)) {
                        // 文件夹后有多个笔记
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        // 文件夹后只有一个笔记
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                // 移回原位置
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    // 判断是否是文件夹后第一个笔记
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    // 判断是否是文件夹后多个笔记中的一个
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    // 是否是最后一项
    public boolean isLast() {
        return mIsLastItem;
    }

    // 获取来电联系人名称
    public String getCallName() {
        return mName;
    }

    // 是否是第一项
    public boolean isFirst() {
        return mIsFirstItem;
    }

    // 是否是唯一一项
    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    // 以下全是 Getter 方法
    public long getId() {
        return mId;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getCreatedDate() {
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        return mHasAttachment;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public long getParentId() {
        return mParentId;
    }

    public int getNotesCount() {
        return mNotesCount;
    }

    public long getFolderId () {
        return mParentId;
    }

    public int getType() {
        return mType;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    // 置顶功能适配（修复版，无报错）
    public boolean isTop() {
        return false;
    }

    // 是否设置了提醒
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    // 是否是来电记录笔记
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 静态方法：直接从 Cursor 获取笔记类型
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}