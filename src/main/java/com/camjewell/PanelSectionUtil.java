package com.camjewell;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;

class PanelSectionUtil {
    private PanelSectionUtil() {
    }

    static JPanel createSeparator(int paddingTopBottom) {
        JSeparator separator = new JSeparator();
        separator.setForeground(java.awt.Color.DARK_GRAY);
        JPanel separatorPanel = new JPanel(new BorderLayout());
        separatorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        separatorPanel.setBorder(new EmptyBorder(paddingTopBottom, 0, paddingTopBottom, 0));
        separatorPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, paddingTopBottom * 2 + 1));
        separatorPanel.add(separator, BorderLayout.CENTER);
        return separatorPanel;
    }
}
