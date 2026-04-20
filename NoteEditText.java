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
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义编辑框控件 NoteEditText
 * 用途：清单模式（CheckList）下的多行编辑框，实现回车换行、删除空行、链接识别等功能
 * 基于 EditText 扩展，供 NoteEditActivity 使用
 */
public class NoteEditText extends androidx.appcompat.widget.AppCompatEditText {
    // 日志标签
    private static final String TAG = "NoteEditText";
    // 当前编辑框在列表中的索引位置
    private int mIndex;
    // 记录删除按键按下前的光标起始位置
    private int mSelectionStartBeforeDelete;

    // 链接协议：电话
    private static final String SCHEME_TEL = "tel:" ;
    // 链接协议：网页
    private static final String SCHEME_HTTP = "http:" ;
    // 链接协议：邮件
    private static final String SCHEME_EMAIL = "mailto:" ;

    // 链接协议与对应字符串资源的映射表
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * 文本变化回调接口
     * 由 NoteEditActivity 实现，用于监听删除、回车、文本内容变化
     */
    public interface OnTextViewChangeListener {
        /**
         * 当按下删除键且当前行内容为空时，删除当前编辑项
         */
        void onEditTextDelete(int index, String text);

        /**
         * 当按下回车键时，在当前编辑项后新增一行
         */
        void onEditTextEnter(int index, String text);

        /**
         * 文本变化时，显示或隐藏列表项的操作按钮
         */
        void onTextChange(int index, boolean hasText);
    }

    // 文本变化监听器对象
    private OnTextViewChangeListener mOnTextViewChangeListener;

    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    /**
     * 设置当前编辑框在列表中的索引
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 设置文本变化的监听器
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 触摸事件处理
     * 作用：点击时精确设置光标的位置
     * 查询：Layout.getLineForVertical / getOffsetForHorizontal 用于文本坐标计算
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 获取触摸点坐标
                int x = (int) event.getX();
                int y = (int) event.getY();
                // 减去内边距，得到内容区域坐标
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                // 加上滚动偏移量
                x += getScrollX();
                y += getScrollY();

                // 获取文本布局对象
                Layout layout = getLayout();
                // 根据Y坐标获取对应行
                int line = layout.getLineForVertical(y);
                // 根据行和X坐标获取对应字符偏移
                int off = layout.getOffsetForHorizontal(line, x);
                // 设置光标到该位置
                Selection.setSelection(getText(), off);
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 按键按下事件
     * 记录删除键按下前的光标位置，回车事件交由抬起处理
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                // 保存删除前光标位置
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 按键抬起事件
     * 处理删除空行、回车换行逻辑
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    // 光标在首位且不是第一行，触发删除当前行
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    // 获取光标位置，将后半部分文本放入新行
                    int selectionStart = getSelectionStart();
                    String text = getText().subSequence(selectionStart, length()).toString();
                    // 保留前半部分文本
                    setText(getText().subSequence(0, selectionStart));
                    // 通知外部插入新行
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变化事件
     * 无焦点且空内容：隐藏勾选框；否则显示
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 创建长按上下文菜单
     * 识别 URLSpan 链接（电话/网址/邮件），生成对应操作菜单
     * 查询：Spanned.getSpans() 获取文本中的样式；URLSpan 实现链接跳转
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            // 计算选中区域最小、最大值
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            // 获取选中区域内的 URL 链接样式
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int defaultResId = 0;
                // 匹配链接协议类型
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                // 未匹配到则使用其他链接
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                // 添加菜单并设置点击跳转
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // 执行链接跳转
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}