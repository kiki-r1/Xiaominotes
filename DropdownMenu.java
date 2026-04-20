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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单封装类
 * 功能：将 Button 封装成带弹出菜单的下拉控件，简化菜单创建逻辑
 * 用途：笔记列表、编辑页中用于展示更多操作选项
 */
public class DropdownMenu {
    private Button mButton;           // 触发菜单的按钮
    private PopupMenu mPopupMenu;    // 系统弹出菜单
    private Menu mMenu;               // 菜单对象

    /**
     * 构造方法
     * @param context 上下文
     * @param button 绑定的按钮
     * @param menuId 菜单布局资源ID
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        // 设置按钮背景为下拉箭头样式
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        // 创建弹出菜单并绑定到按钮
        mPopupMenu = new PopupMenu(context, mButton);
        // 获取菜单对象
        mMenu = mPopupMenu.getMenu();
        // 加载菜单布局
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        // 按钮点击时显示菜单
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置菜单项点击监听器
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据ID查找菜单项
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮显示文字
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}