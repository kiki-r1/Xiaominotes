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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

/**
 * 笔记应用的首选项/设置页面
 * 继承自PreferenceActivity，提供设置界面
 * 主要功能：管理Google Tasks同步账号、手动触发同步、显示上次同步时间
 */
public class NotesPreferenceActivity extends PreferenceActivity {
    // SharedPreferences文件名，用于存储设置数据
    public static final String PREFERENCE_NAME = "notes_preferences";

    // 同步账号名称的存储键名
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    // 上次同步时间的存储键名
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    // 背景颜色随机显示的设置键名（引用自preferences.xml中的CheckBoxPreference）
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    // 同步账号分类的键名，用于在设置界面中找到账号设置分类
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    // 添加账号时的权限过滤器键名
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    // 设置界面中的账号分类（PreferenceCategory），用于容纳账号相关的设置项
    private PreferenceCategory mAccountCategory;

    // Google Tasks同步服务的广播接收器，用于接收同步状态更新
    private GTaskReceiver mReceiver;

    // 存储当前设备上的Google账号列表（用于检测新增账号）
    private Account[] mOriAccounts;

    // 标记用户是否添加了新账号
    private boolean mHasAddedAccount;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 启用应用图标作为导航返回按钮（ActionBar上的返回箭头）
        /* 使用应用图标进行导航，点击可返回上级页面 */
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 从res/xml/preferences.xml加载设置界面布局
        addPreferencesFromResource(R.xml.preferences);

        // 根据键名找到账号设置分类（在preferences.xml中定义）
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);

        // 创建GTask广播接收器实例
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        // 添加要监听的广播动作（GTaskSyncService中定义的广播名称）
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);

        // ✅ 彻底修复：使用 ContextCompat 统一注册，兼容所有Android版本，消除Lint警告
        // 注册广播接收器，RECEIVER_NOT_EXPORTED表示此广播仅限应用内部使用，提高安全性
        ContextCompat.registerReceiver(
                this,
                mReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        mOriAccounts = null;

        // 加载自定义的头部布局（settings_header.xml），包含同步按钮和状态显示
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        // 将头部视图添加到ListView的顶部（ListView是PreferenceActivity内置的）
        getListView().addHeaderView(header, null, true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 如果用户添加了新账号，需要自动设置同步账号
        // 检测逻辑：比较当前账号列表和之前的账号列表，如果新增了账号，则自动设置为同步账号
        if (mHasAddedAccount) {
            // 获取当前设备上的所有Google账号
            Account[] accounts = getGoogleAccounts();
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                // 遍历新账号列表，找出新增的账号
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    // 如果在新账号列表中找到不在旧列表中的账号，则设置为同步账号
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }

        // 刷新UI，更新账号显示和同步按钮状态
        refreshUI();
    }

    @Override
    protected void onDestroy() {
        // 在Activity销毁时取消注册广播接收器，防止内存泄漏
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    /**
     * 加载账号偏好设置项
     * 在设置界面中创建一个可点击的Preference，用于显示和选择同步账号
     */
    private void loadAccountPreference() {
        // 清空账号分类下的所有现有设置项
        mAccountCategory.removeAll();

        // 创建一个新的Preference对象（非CheckBoxPreference，而是普通可点击项）
        Preference accountPref = new Preference(this);
        // 获取当前保存的同步账号名
        final String defaultAccount = getSyncAccountName(this);

        // 设置Preference的标题和摘要（引用strings.xml中的字符串资源）
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));

        // 设置点击事件监听器
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                // 只有在没有进行同步操作时才能更改账号
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // 首次设置账号：显示账号选择对话框
                        showSelectAccountAlertDialog();
                    } else {
                        // 已设置账号：提示用户更换账号的风险
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    // 同步进行中，提示无法更改账号
                    Toast.makeText(NotesPreferenceActivity.this,
                                    R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        // 将创建的Preference添加到账号分类中
        mAccountCategory.addPreference(accountPref);
    }

    /**
     * 加载同步按钮和状态显示
     * 从settings_header.xml中获取按钮和文本框，设置其状态和点击行为
     */
    private void loadSyncButton() {
        // 通过findViewById获取头部布局中的按钮和文本（头部布局已通过addHeaderView添加）
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // 根据同步状态设置按钮文本和点击行为
        if (GTaskSyncService.isSyncing()) {
            // 正在同步中：按钮显示"取消同步"
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // 调用取消同步的静态方法
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            // 未同步：按钮显示"立即同步"
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // 调用开始同步的静态方法
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        // 如果没有设置同步账号，则禁用同步按钮
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // 设置上次同步时间的显示
        if (GTaskSyncService.isSyncing()) {
            // 同步中：显示同步进度信息
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            // 非同步状态：从SharedPreferences读取上次同步时间
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                // 格式化时间并显示（引用preferences_last_sync_time_format定义的时间格式）
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                // 从未同步过，隐藏时间显示
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 刷新整个设置界面UI
     * 重新加载账号设置和同步按钮状态
     */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    /**
     * 显示选择账号的对话框
     * 使用AlertDialog.Builder创建单选列表对话框，列出所有Google账号供用户选择
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 加载自定义标题布局（account_dialog_title.xml）
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null); // 不设置确定按钮

        // 获取所有Google账号
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        // 保存当前账号列表，用于检测后续是否添加了新账号
        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            // 创建账号名称数组用于列表显示
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                // 标记当前已设置的账号为选中状态
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }
            // 设置单选列表
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // 用户选择账号后，设置同步账号并关闭对话框
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        // 加载"添加账号"文本布局（add_account_text.xml）
        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();
        // 为"添加账号"文本设置点击事件，跳转到系统添加账号界面
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true; // 标记用户添加了新账号
                // 跳转到系统添加账号设置界面
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                // 添加权限过滤器，只显示支持gmail-ls权限的账号类型（即Google账号）
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                        "gmail-ls"
                });
                startActivityForResult(intent, -1);
                dialog.dismiss();
            }
        });
    }

    /**
     * 显示更换账号的确认对话框
     * 当用户已有同步账号时，提示更换账号的风险，并提供更换、移除、取消三个选项
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 加载并设置对话框标题（显示当前账号名）
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        // 定义菜单项数组：更换账号、移除账号、取消
        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    // 更换账号：显示账号选择对话框
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    // 移除账号：清除同步账号信息
                    removeSyncAccount();
                    refreshUI();
                }
                // which == 2 时取消，不做任何操作
            }
        });
        dialogBuilder.show();
    }

    /**
     * 获取设备上的所有Google账号
     * 使用AccountManager系统服务获取类型为"com.google"的账号
     * @return Google账号数组
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    /**
     * 设置同步账号
     * 将账号名保存到SharedPreferences，并清理本地的GTask相关信息
     * @param account 要设置的账号名
     */
    private void setSyncAccount(String account) {
        // 只有当新账号与当前账号不同时才执行更新
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit(); // 注意：commit是同步写入，考虑性能可改用apply

            // 重置上次同步时间为0
            setLastSyncTime(this, 0);

            // 在新线程中清理本地GTask相关信息（GTASK_ID和SYNC_ID）
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");      // 清空Google Tasks ID
                    values.put(NoteColumns.SYNC_ID, 0);        // 清空同步ID
                    // 更新笔记数据库中所有记录的GTask字段
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            // 显示设置成功的提示消息
            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 移除同步账号
     * 清除SharedPreferences中保存的账号名和上次同步时间，并清理本地GTask数据
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // 在新线程中清理本地GTask相关信息
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    /**
     * 获取当前保存的同步账号名（静态工具方法）
     * @param context 上下文对象
     * @return 账号名，未设置时返回空字符串
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /**
     * 保存上次同步时间（静态工具方法）
     * @param context 上下文对象
     * @param time 时间戳（毫秒）
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    /**
     * 获取上次同步时间（静态工具方法）
     * @param context 上下文对象
     * @return 时间戳，未同步过返回0
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /**
     * Google Tasks同步服务的广播接收器
     * 用于接收GTaskSyncService发送的同步状态广播，实时更新UI
     */
    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // 刷新UI（更新账号显示和同步按钮状态）
            refreshUI();
            // 如果广播中包含同步状态信息，更新同步进度文本
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }
        }
    }

    /**
     * 处理ActionBar上的菜单项点击事件
     * @param item 被点击的菜单项
     * @return true表示事件已处理
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:  // ActionBar上的返回箭头（android系统自带的ID）
                // 返回到笔记列表页面
                Intent intent = new Intent(this, NotesListActivity.class);
                // 添加FLAG_ACTIVITY_CLEAR_TOP标志：清除目标Activity之上的所有Activity
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}