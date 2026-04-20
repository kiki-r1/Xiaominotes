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

import android.content.Context;
import android.text.SpannableString;      // 可样式化的字符串，用于设置高亮
import android.text.TextUtils;            // 文本工具类，用于判断字符串是否为空
import android.text.style.ForegroundColorSpan;  // 前景色跨度，用于改变文字颜色
import android.text.format.DateUtils;     // 日期工具类，用于格式化相对时间
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

import java.util.regex.Matcher;   // 正则匹配器
import java.util.regex.Pattern;   // 正则模式

/**
 * 笔记列表项的自定义布局
 *
 * 功能说明：
 * 1. 显示笔记或文件夹的列表项
 * 2. 支持不同类型的显示：普通笔记、通话记录笔记、文件夹
 * 3. 支持多选模式（带复选框）
 * 4. 支持搜索关键词高亮显示（红色）
 * 5. 根据笔记位置自动设置背景样式（第一项、最后一项、中间项等）
 *
 * 使用场景：
 * - NotesListActivity 中的笔记列表
 * - 文件夹内容列表
 * - 搜索结果列表
 */
public class NotesListItem extends LinearLayout {

    // ====================== UI组件 ======================
    private final ImageView mAlert;      // 闹钟提醒图标（时钟图标）
    private final TextView mTitle;       // 标题/摘要文本
    private final TextView mTime;        // 最后修改时间
    private final TextView mCallName;    // 通话记录中的联系人姓名
    private NoteItemData mItemData;      // 列表项数据模型
    private final CheckBox mCheckBox;    // 多选模式下的复选框

    // ====================== 搜索高亮 ======================
    // 静态变量，存储当前搜索关键词
    // 所有列表项共享同一个搜索关键词，用于高亮显示
    private static String sSearchKeyword = "";

    /**
     * 设置搜索关键词（静态方法）
     * 供外部调用，设置当前搜索的关键词
     * 所有列表项都会使用这个关键词进行高亮匹配
     *
     * @param keyword 搜索关键词
     */
    public static void setSearchKeyword(String keyword) {
        sSearchKeyword = keyword;
    }

    /**
     * 构造函数
     * 加载布局文件并初始化UI组件
     *
     * @param context 上下文对象
     */
    public NotesListItem(Context context) {
        super(context);
        // 加载 note_item.xml 布局文件
        inflate(context, R.layout.note_item, this);

        // 初始化UI组件
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);      // 闹钟图标
        mTitle = (TextView) findViewById(R.id.tv_title);            // 标题
        mTime = (TextView) findViewById(R.id.tv_time);              // 时间
        mCallName = (TextView) findViewById(R.id.tv_name);          // 通话联系人姓名

        // 获取复选框（android.R.id.checkbox 是系统内置ID）
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 绑定数据到列表项
     * 根据笔记类型和状态设置UI显示
     *
     * @param context    上下文
     * @param data       笔记数据对象
     * @param choiceMode 是否处于多选模式
     * @param checked    当前项是否被选中（多选模式下）
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {

        // ====================== 处理多选模式复选框 ======================
        // 多选模式下且当前项是笔记类型时，显示复选框
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);   // 显示复选框
            mCheckBox.setChecked(checked);            // 设置选中状态
        } else {
            mCheckBox.setVisibility(View.GONE);       // 非多选模式隐藏复选框
        }

        // 保存数据对象
        mItemData = data;

        // ====================== 根据笔记类型设置显示样式 ======================

        // 情况1：通话记录文件夹（特殊文件夹）
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 隐藏联系人姓名
            mCallName.setVisibility(View.GONE);
            // 显示闹钟图标区域（这里实际显示的是通话记录图标）
            mAlert.setVisibility(View.VISIBLE);
            // 设置标题样式为主文本样式
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            // 设置标题文字："通话记录 (数量)"
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            // 设置图标为通话记录图标
            mAlert.setImageResource(R.drawable.call_record);
        }
        // 情况2：通话记录文件夹下的笔记（通话录音笔记）
        else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 显示联系人姓名
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            // 标题使用次要文本样式
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem);

            // 获取格式化的摘要内容（去除多余空白、换行等）
            String snippet = DataUtils.getFormattedSnippet(data.getSnippet());
            // 高亮显示搜索关键词
            mTitle.setText(highlightText(snippet));

            // 如果有提醒闹钟，显示时钟图标
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        }
        // 情况3：普通笔记或文件夹
        else {
            // 隐藏联系人姓名
            mCallName.setVisibility(View.GONE);
            // 标题使用主文本样式
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            // 子情况3.1：文件夹类型
            if (data.getType() == Notes.TYPE_FOLDER) {
                // 显示文件夹名称和包含的笔记数量
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
                // 文件夹不显示闹钟图标
                mAlert.setVisibility(View.GONE);
            }
            // 子情况3.2：普通笔记类型
            else {
                // 获取格式化的摘要内容
                String snippet = DataUtils.getFormattedSnippet(data.getSnippet());
                // 高亮显示搜索关键词
                mTitle.setText(highlightText(snippet));

                // 如果有提醒闹钟，显示时钟图标
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // ====================== 设置修改时间 ======================
        // DateUtils.getRelativeTimeSpanString() 将时间转换为相对时间格式
        // 例如："刚刚"、"5分钟前"、"2小时前"、"昨天"等
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // ====================== 设置背景样式 ======================
        // 根据笔记在列表中的位置和类型设置不同的背景
        // 使列表项有圆角效果（顶部圆角、底部圆角、中间直角等）
        setBackground(data);
    }

    /**
     * 高亮关键词（标红）
     *
     * 功能：在文本中搜索关键词，将所有匹配的关键词标记为红色
     *
     * 实现原理：
     * 1. 使用正则表达式匹配关键词（不区分大小写）
     * 2. 使用 SpannableString 设置 ForegroundColorSpan 改变文字颜色
     * 3. 循环匹配直到文本末尾
     *
     * @param content 原始文本内容
     * @return 处理后的 CharSequence（如果有关键词则带红色高亮，否则返回原文本）
     */
    private CharSequence highlightText(String content) {
        // 如果内容为空或搜索关键词为空，直接返回原内容
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(sSearchKeyword)) {
            return content;
        }

        // 创建可样式化的字符串
        SpannableString sp = new SpannableString(content);

        // 编译正则表达式：不区分大小写
        Pattern pattern = Pattern.compile(sSearchKeyword, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sp);

        // 遍历所有匹配项，设置红色高亮
        while (matcher.find()) {
            // 0xFFFF0000 是红色（ARGB格式：Alpha=FF, Red=FF, Green=00, Blue=00）
            sp.setSpan(new ForegroundColorSpan(0xFFFF0000),
                    matcher.start(),      // 匹配开始位置
                    matcher.end(),        // 匹配结束位置
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);  // 不包含边界
        }
        return sp;
    }

    /**
     * 设置列表项背景
     *
     * 根据笔记类型和在列表中的位置设置不同的背景资源：
     *
     * 对于笔记类型（TYPE_NOTE）：
     * - 单独的笔记（列表中只有一个）→ 四周圆角
     * - 第一项 → 顶部圆角
     * - 最后一项 → 底部圆角
     * - 中间项 → 无圆角
     *
     * 对于文件夹类型（TYPE_FOLDER）：
     * - 使用固定的文件夹背景
     *
     * @param data 笔记数据对象
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();  // 获取背景颜色ID（黄、红、蓝、绿、白等）

        if (data.getType() == Notes.TYPE_NOTE) {
            // 笔记类型：根据位置选择不同的背景资源
            if (data.isSingle() || data.isOneFollowingFolder()) {
                // 单独一项 或 后面紧跟着文件夹 → 使用四角圆角背景
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                // 最后一项 → 使用底部圆角背景
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                // 第一项 或 后面跟着多个文件夹 → 使用顶部圆角背景
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                // 中间项 → 使用直角背景（无圆角）
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            // 文件夹类型：使用固定的文件夹背景
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前列表项的数据对象
     *
     * @return NoteItemData 对象，包含当前项的完整信息
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}