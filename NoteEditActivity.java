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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlarmManager;      // 闹钟管理服务，用于设置提醒
import android.app.AlertDialog;       // 警告对话框，用于删除确认
import android.app.PendingIntent;     // 延迟意图，用于闹钟触发
import android.app.SearchManager;     // 搜索管理器，处理搜索高亮
import android.appwidget.AppWidgetManager;  // 桌面小部件管理器
import android.content.ContentUris;   // Content URI 构建工具
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;  // 轻量级数据存储，保存字体大小设置
import android.graphics.Paint;        // 画笔，用于设置删除线效果
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;        // 可样式化的文本
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;  // 日期格式化工具
import android.text.style.BackgroundColorSpan;  // 背景色跨度，用于搜索高亮
import android.view.LayoutInflater;    // 布局填充器，动态加载列表项
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;   // 哈希映射，用于按钮ID到颜色/字体大小的映射
import java.util.HashSet;   // 哈希集合，用于批量删除笔记ID
import java.util.Map;
import java.util.regex.Matcher;  // 正则匹配器
import java.util.regex.Pattern;  // 正则模式

/**
 * 笔记编辑Activity
 * 功能：
 * 1. 支持普通编辑模式（标题 + 正文）
 * 2. 支持清单模式（带复选框的待办事项列表）
 * 3. 支持设置背景颜色、字体大小
 * 4. 支持设置提醒闹钟
 * 5. 支持搜索关键词高亮
 * 6. 支持分享、添加到桌面快捷方式
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {

    /**
     * 头部视图持有者
     * 用于缓存顶部栏的视图组件，提高性能
     */
    private class HeadViewHolder {
        public TextView tvModified;      // 最后修改时间
        public ImageView ivAlertIcon;    // 闹钟图标
        public TextView tvAlertDate;     // 提醒日期时间
        public ImageView ibSetBgColor;   // 设置背景颜色按钮
    }

    // ====================== 背景颜色映射表 ======================
    // 按钮ID → 背景颜色资源ID
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    // 背景颜色资源ID → 对应的选中图标ID
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    // ====================== 字体大小映射表 ======================
    // 按钮ID → 字体大小资源ID
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    // 字体大小资源ID → 对应的选中图标ID
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";
    private HeadViewHolder mNoteHeaderHolder;      // 头部视图缓存
    private View mHeadViewPanel;                    // 头部面板
    private View mNoteBgColorSelector;              // 背景颜色选择器面板
    private View mFontSizeSelector;                 // 字体大小选择器面板

    // ====================== 双输入框组件 ======================
    private EditText mNoteTitle;       // 标题输入框
    private EditText mNoteContent;     // 正文输入框
    // ========================================================

    private View mNoteEditorPanel;                  // 笔记编辑区域面板
    private WorkingNote mWorkingNote;               // 工作笔记模型，封装笔记数据操作
    private SharedPreferences mSharedPrefs;         // 偏好设置存储
    private int mFontSizeId;                         // 当前字体大小ID
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";  // 字体大小存储键
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;  // 快捷方式标题最大长度

    // 清单模式特殊标记：✓ 表示已完成，□ 表示未完成
    public static final String TAG_CHECKED = String.valueOf('\u221A');     // 勾选标记
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');   // 未勾选标记

    private LinearLayout mEditTextList;     // 清单模式下的动态列表容器
    private String mUserQuery;               // 用户搜索关键词
    private Pattern mPattern;                // 正则匹配模式，用于搜索高亮

    // ====================== Activity生命周期方法 ======================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_edit);  // 设置布局文件

        // 初始化Activity状态，如果失败则关闭Activity
        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();  // 初始化UI资源
    }

    /**
     * 恢复Activity状态时调用
     * 从保存的Bundle中恢复笔记ID并重新初始化
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
        }
    }

    /**
     * 初始化Activity状态
     * 根据Intent的Action类型决定是加载已有笔记还是创建新笔记
     *
     * @param intent 启动Activity的Intent
     * @return 初始化是否成功
     */
    private boolean initActivityState(Intent intent) {
        mWorkingNote = null;

        // 情况1：查看已有笔记（ACTION_VIEW）
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            // 处理搜索跳转：从搜索结果中点击进入
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            // 验证笔记是否存在且可见
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                // 笔记不存在，跳转到笔记列表
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                // 加载笔记数据
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    finish();
                    return false;
                }
            }
            // 设置软键盘：默认隐藏，调整窗口大小
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        }
        // 情况2：新建或编辑笔记（ACTION_INSERT_OR_EDIT）
        else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, ResourceParser.getDefaultBgId(this));

            // 处理通话记录笔记：根据电话号码和通话日期查找或创建
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                long noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(
                        getContentResolver(), phoneNumber, callDate);
                if (noteId > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                // 创建空白笔记
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId);
            }
            // 设置软键盘：调整窗口大小，默认显示软键盘
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        else {
            finish();
            return false;
        }

        // 设置笔记设置变化监听器
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();  // 刷新笔记界面
    }

    /**
     * 初始化笔记界面显示
     * 根据当前笔记类型（普通/清单）和内容设置UI
     */
    private void initNoteScreen() {
        // 设置字体样式
        mNoteTitle.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        mNoteTitle.setTextSize(28); // 标题字体加大
        mNoteContent.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));

        // 根据笔记模式切换UI
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 清单模式：隐藏标题/正文，显示动态列表
            mNoteTitle.setVisibility(View.GONE);
            mNoteContent.setVisibility(View.GONE);
            mEditTextList.setVisibility(View.VISIBLE);
            switchToListMode(mWorkingNote.getContent());
        } else {
            // 普通模式：显示标题/正文，隐藏列表
            mNoteTitle.setVisibility(View.VISIBLE);
            mNoteContent.setVisibility(View.VISIBLE);
            mEditTextList.setVisibility(View.GONE);

            // 拆分内容：第一行作为标题，剩余作为正文
            String content = mWorkingNote.getContent();
            if (content == null) content = "";

            int lineBreak = content.indexOf('\n');
            if (lineBreak >= 0) {
                mNoteTitle.setText(content.substring(0, lineBreak));
                mNoteContent.setText(content.substring(lineBreak + 1));
            } else {
                mNoteTitle.setText(content);
                mNoteContent.setText("");
            }
        }

        // 隐藏所有背景选择器的选中标记
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }

        // 设置背景颜色
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        // 设置修改时间显示
        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        // 显示提醒头部（如果有闹钟）
        showAlertHeader();
    }

    /**
     * 显示闹钟提醒头部信息
     * 如果笔记设置了提醒，显示提醒时间；如果已过期，显示"已过期"
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long now = System.currentTimeMillis();
            if (now > mWorkingNote.getAlertDate()) {
                // 提醒时间已过
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                // 显示相对时间（如"2小时后"）
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), now, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            // 无提醒，隐藏相关控件
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        }
    }

    /**
     * 当Activity被新Intent启动时调用
     * 用于处理从外部跳转（如搜索、小部件）的情况
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    /**
     * 保存Activity状态
     * 在Activity被销毁前保存笔记ID，以便恢复
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!mWorkingNote.existInDatabase()) {
            saveNote();  // 如果笔记未保存，先保存
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
    }

    /**
     * 分发触摸事件
     * 实现点击选择器外部自动关闭的功能
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 如果背景颜色选择器可见且点击在其外部，则关闭
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }
        // 如果字体大小选择器可见且点击在其外部，则关闭
        if (mFontSizeSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 判断触摸点是否在指定视图范围内
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        return !(ev.getX() < x || ev.getX() > x + view.getWidth()
                || ev.getY() < y || ev.getY() > y + view.getHeight());
    }

    /**
     * 初始化UI资源
     * 绑定控件、设置监听器
     */
    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);

        // 初始化头部视图持有者
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);

        // ====================== 绑定双输入框 ======================
        mNoteTitle = findViewById(R.id.note_edit_title);
        mNoteContent = findViewById(R.id.note_edit_content);
        // =========================================================

        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);

        // 设置背景颜色选择按钮点击监听
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = findViewById(id);
            iv.setOnClickListener(this);
        }

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        // 设置字体大小选择按钮点击监听
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        }

        // 读取保存的字体大小设置
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);

        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }

        mEditTextList = findViewById(R.id.note_edit_list);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote();           // 保存笔记
        clearSettingState();  // 关闭所有选择器面板
    }

    /**
     * 更新桌面小部件
     * 当笔记内容变化时，同步更新关联的小部件
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            return;
        }
        int[] ids = {mWorkingNote.getWidgetId()};
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    // ====================== 点击事件处理 ======================

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // 点击设置背景颜色按钮：显示颜色选择器
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.VISIBLE);
        }
        // 点击背景颜色选项：更改笔记背景色
        else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        }
        // 点击字体大小选项：更改字体大小并保存设置
        else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).apply();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);

            // 应用字体大小到输入框
            mNoteTitle.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            mNoteContent.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        // 如果选择器面板打开，优先关闭面板
        if (clearSettingState()) return;
        saveNote();
        super.onBackPressed();
    }

    /**
     * 关闭所有设置面板
     * @return 是否有面板被关闭
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    // ====================== NoteSettingChangedListener 接口实现 ======================

    /**
     * 背景颜色改变时的回调
     * 更新界面背景
     */
    @Override
    public void onBackgroundColorChanged() {
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    // ====================== 选项菜单 ======================

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) return true;
        clearSettingState();  // 打开菜单时关闭选择器
        menu.clear();

        // 根据笔记类型加载不同的菜单
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }

        // 动态设置清单模式菜单项文字
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }

        // 根据是否有提醒动态显示/隐藏菜单项
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
            menu.findItem(R.id.menu_delete_remind).setVisible(true);
        } else {
            menu.findItem(R.id.menu_alert).setVisible(true);
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_new_note) {
            createNewNote();                    // 新建笔记
        } else if (itemId == R.id.menu_delete) {
            showDeleteConfirmDialog();          // 删除笔记确认
        } else if (itemId == R.id.menu_font_size) {
            showFontSizeSelector();             // 显示字体大小选择器
        } else if (itemId == R.id.menu_list_mode) {
            toggleListMode();                   // 切换清单/普通模式
        } else if (itemId == R.id.menu_share) {
            shareNote();                        // 分享笔记
        } else if (itemId == R.id.menu_send_to_desktop) {
            sendToDesktop();                    // 添加到桌面快捷方式
        } else if (itemId == R.id.menu_alert) {
            setReminder();                      // 设置提醒
        } else if (itemId == R.id.menu_delete_remind) {
            mWorkingNote.setAlertDate(0, false); // 删除提醒
        }
        return true;
    }

    /**
     * 显示删除笔记的确认对话框
     */
    private void showDeleteConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.alert_title_delete);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.alert_message_delete_note);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            deleteCurrentNote();
            finish();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /**
     * 显示字体大小选择器
     */
    private void showFontSizeSelector() {
        mFontSizeSelector.setVisibility(View.VISIBLE);
        findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
    }

    /**
     * 切换清单模式和普通模式
     */
    private void toggleListMode() {
        mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0
                ? TextNote.MODE_CHECK_LIST : 0);
    }

    /**
     * 分享笔记内容
     */
    private void shareNote() {
        getWorkingText();
        sendTo(this, mWorkingNote.getContent());
    }

    /**
     * 设置提醒闹钟
     * 弹出日期时间选择器
     */
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener((dialog, date) -> mWorkingNote.setAlertDate(date, true));
        d.show();
    }

    /**
     * 分享文本到其他应用
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        startActivity(intent);
    }

    /**
     * 创建新笔记
     * 保存当前笔记后跳转到新建笔记界面
     */
    private void createNewNote() {
        saveNote();
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前笔记
     * 根据是否同步模式决定是彻底删除还是移动到回收站
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            }
            if (!isSyncMode()) {
                // 非同步模式：彻底删除
                DataUtils.batchDeleteNotes(getContentResolver(), ids);
            } else {
                // 同步模式：移动到回收站
                DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER);
            }
        }
        mWorkingNote.markDeleted(true);
    }

    /**
     * 判断是否处于同步模式
     * 检查是否配置了同步账号
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    // ====================== 闹钟提醒相关 ======================

    /**
     * 闹钟提醒变化回调
     * @param date 提醒时间
     * @param set true=设置提醒，false=取消提醒
     */
    @Override
    public void onClockAlertChanged(long date, boolean set) {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            // 创建闹钟Intent
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            showAlertHeader();

            if (!set) {
                alarmManager.cancel(pendingIntent);  // 取消闹钟
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);  // 设置闹钟
            }
        } else {
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    @Override
    public void onWidgetChanged() {
        updateWidget();  // 更新桌面小部件
    }

    // ====================== 清单模式列表项操作 ======================

    /**
     * 删除列表项时的回调
     * @param index 删除项的位置
     * @param text 删除项的文本内容（用于追加到上一项）
     */
    @Override
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) return;  // 至少保留一个空项

        // 更新后续项的索引
        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i - 1);
        }
        mEditTextList.removeViewAt(index);

        // 将删除的文本追加到上一项末尾
        NoteEditText edit = (NoteEditText) mEditTextList.getChildAt(Math.max(0, index - 1))
                .findViewById(R.id.et_edit_text);
        int len = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(len);
    }

    /**
     * 按回车键时创建新列表项
     * @param index 当前项的位置
     * @param text 当前项的文本
     */
    @Override
    public void onEditTextEnter(int index, String text) {
        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);

        // 更新后续项的索引
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i);
        }
    }

    /**
     * 切换到清单模式
     * 将文本按换行符拆分成多个列表项
     * @param text 原始文本
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index++));
            }
        }
        // 添加一个空的输入项供用户输入
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();
    }

    /**
     * 高亮显示搜索结果
     * @param fullText 完整文本
     * @param userQuery 搜索关键词
     * @return 带高亮样式的Spannable文本
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(new BackgroundColorSpan(getResources().getColor(R.color.user_query_highlight)),
                        m.start(), m.end(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 创建清单模式下的列表项视图
     * @param item 项文本内容
     * @param index 项索引
     * @return 列表项视图
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = view.findViewById(R.id.cb_edit_item);

        // 设置复选框变化监听：勾选时添加删除线
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                edit.setPaintFlags(Paint.DEV_KERN_TEXT_FLAG);
            }
        });

        // 解析已完成/未完成标记
        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            item = item.substring(TAG_UNCHECKED.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * 文本变化回调
     * 根据是否有内容显示/隐藏复选框
     */
    @Override
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) return;
        mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item)
                .setVisibility(hasText ? View.VISIBLE : View.GONE);
    }

    /**
     * 清单模式切换回调
     * 在两种模式间切换时保存并重新加载界面
     */
    @Override
    public void onCheckListModeChanged(int oldMode, int newMode) {
        getWorkingText();  // 保存当前编辑内容

        if (newMode == TextNote.MODE_CHECK_LIST) {
            // 切换到清单模式
            mNoteTitle.setVisibility(View.GONE);
            mNoteContent.setVisibility(View.GONE);
            mEditTextList.setVisibility(View.VISIBLE);
            switchToListMode(mWorkingNote.getContent());
        } else {
            // 切换到普通模式
            mEditTextList.setVisibility(View.GONE);
            mNoteTitle.setVisibility(View.VISIBLE);
            mNoteContent.setVisibility(View.VISIBLE);

            // 将清单内容合并为文本：第一行标题，后续为正文
            String content = mWorkingNote.getContent();
            int lineBreak = content.indexOf('\n');
            if (lineBreak >= 0) {
                mNoteTitle.setText(content.substring(0, lineBreak));
                mNoteContent.setText(content.substring(lineBreak + 1));
            } else {
                mNoteTitle.setText(content);
                mNoteContent.setText("");
            }
        }
    }

    // ====================== 笔记保存逻辑 ======================

    /**
     * 获取当前编辑的文本内容
     * @return 是否有勾选的项（清单模式下）
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;

        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 清单模式：收集所有列表项，添加标记后拼接
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            // 普通模式：拼接标题和正文
            String title = mNoteTitle.getText().toString();
            String body = mNoteContent.getText().toString();
            mWorkingNote.setWorkingText(title + "\n" + body);
        }
        return hasChecked;
    }

    /**
     * 保存笔记
     * 核心保存逻辑：
     * 1. 如果笔记完全为空，删除笔记
     * 2. 如果正文有内容但标题为空，自动生成"未命名+时间"作为标题
     * 3. 正常保存
     */
    private void saveNote() {
        getWorkingText();  // 先获取当前编辑内容
        String content = mWorkingNote.getContent();

        // 1. 如果完全为空 → 不保存，直接删除
        if (TextUtils.isEmpty(content) || content.trim().isEmpty()) {
            if (mWorkingNote.existInDatabase()) {
                deleteCurrentNote();
            }
            return;
        }

        // 2. 拆分标题和正文
        String title;
        String body;
        int lineBreak = content.indexOf('\n');
        if (lineBreak >= 0) {
            title = content.substring(0, lineBreak).trim();
            body = content.substring(lineBreak + 1).trim();
        } else {
            title = content.trim();
            body = "";
        }

        // 3. 正文有内容，但标题为空 → 自动设置"未命名+时间"
        if (!TextUtils.isEmpty(body) && TextUtils.isEmpty(title)) {
            // 格式化时间：2025-12-29 15:30
            String time = DateUtils.formatDateTime(
                    this,
                    System.currentTimeMillis(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE
                            | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR
            );
            title = "未命名 " + time;

            // 重新拼接内容并保存
            content = title + "\n" + body;
            mWorkingNote.setWorkingText(content);
        }

        // 4. 正常保存
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            setResult(RESULT_OK);
        }
    }

    /**
     * 发送到桌面快捷方式
     * 创建指向当前笔记的快捷图标
     */
    private void sendToDesktop() {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            // 创建快捷方式的Intent
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());

            // 创建快捷方式并发送广播
            Intent sender = new Intent();
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME, makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成快捷方式标题
     * 去除清单标记，限制最大长度
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "").replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN
                ? content.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN)
                : content;
    }

    /**
     * 显示Toast消息
     */
    private void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}