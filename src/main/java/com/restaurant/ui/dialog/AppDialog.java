package com.restaurant.ui.dialog;

import com.restaurant.ui.RoundedButton;
import com.restaurant.ui.UIConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * AppDialog — Phase 4 Base Class
 *
 * <p>Standard layout:
 * <pre>
 * ┌─────────────────────────── Header (PRIMARY) ─────── [×] ─┐
 * │  Title                                                     │
 * ├────────────────────────────────────────────────────────────┤
 * │  Body  (scrollable, MigLayout 2-col: label | input)        │
 * ├────────────────────────────────────────────────────────────┤
 * │                                 [Cancel]  [Save ▸]         │
 * └────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Keyboard shortcuts:
 * <ul>
 *   <li>{@code ESC} → {@link #onCancel()}</li>
 *   <li>{@code ENTER} → {@link #onSave()} (unless focus is in multi-line component)</li>
 * </ul>
 *
 * <p>Subclasses implement:
 * <ul>
 *   <li>{@link #buildBody()} — return the form panel</li>
 *   <li>{@link #onSave()} — validate + persist, call {@link #close()} on success</li>
 *   <li>{@link #getDialogTitle()} — title string shown in header</li>
 *   <li>{@link #getSaveLabel()} — override to customise the save button text</li>
 * </ul>
 */
public abstract class AppDialog extends JDialog {

    // ── Design tokens ────────────────────────────────────────────────────────
    static final Color HEADER_BG     = UIConstants.PRIMARY;
    static final Color HEADER_FG     = Color.WHITE;
    static final Color FOOTER_BG     = new Color(0xF8FAFC);
    static final Color FOOTER_BORDER = new Color(0xE2E8F0);
    static final Color BODY_BG       = Color.WHITE;

    static final int MIN_WIDTH  = 500;
    static final int MAX_WIDTH  = 700;
    static final int HEADER_H   = 58;
    static final int FOOTER_H   = 60;

    // ── Subclass hooks ───────────────────────────────────────────────────────

    /** Title shown in the coloured header bar. */
    protected abstract String getDialogTitle();

    /**
     * Build and return the form body panel.
     * Use {@link FormBuilder} for consistent 2-column rows.
     */
    protected abstract JPanel buildBody();

    /**
     * Called when the user presses Save / Enter.
     * Validate fields; call {@link #close()} only on success.
     */
    protected abstract void onSave();

    // ── Optional overrides ───────────────────────────────────────────────────
    protected String  getSaveLabel()   { return "Lưu"; }
    protected String  getCancelLabel() { return "Hủy"; }
    /** Called when the dialog is cancelled (ESC / Cancel button). */
    protected void    onCancel()       { close(); }

    // ── Internal state ───────────────────────────────────────────────────────
    private RoundedButton btnSave;

    // ── Constructor ──────────────────────────────────────────────────────────

    protected AppDialog(Window owner) {
        super(owner, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);           // We paint our own chrome
        setBackground(new Color(0, 0, 0, 0));
        buildShell();
        installKeyboardShortcuts();
    }

    // ── Shell builder ────────────────────────────────────────────────────────

    private void buildShell() {
        // Outer panel — provides drop shadow and rounded clip
        JPanel shell = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Drop shadow
                for (int i = 4; i > 0; i--) {
                    g2.setColor(new Color(0, 0, 0, 15 * i));
                    g2.fillRoundRect(i, i, getWidth() - i * 2, getHeight() - i * 2, 14, 14);
                }
                // White card
                g2.setColor(BODY_BG);
                g2.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 12, 12);
                g2.dispose();
            }
        };
        shell.setOpaque(false);
        shell.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 8)); // shadow room

        // ── Header ────────────────────────────────────────────────────────
        shell.add(buildHeader(), BorderLayout.NORTH);

        // ── Body (scrollable) ─────────────────────────────────────────────
        JPanel body = buildBody();
        body.setBackground(BODY_BG);
        body.setBorder(BorderFactory.createEmptyBorder(16, 24, 8, 24));

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BODY_BG);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        shell.add(scroll, BorderLayout.CENTER);

        // ── Footer ────────────────────────────────────────────────────────
        shell.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(shell);
        // Width range
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, getWidth()));
                if (w != getWidth()) setSize(w, getHeight());
            }
        });
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Rounded top only
                g2.setColor(HEADER_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 12, 12, 12);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setPreferredSize(new Dimension(0, HEADER_H));
        header.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 10));

        JLabel lblTitle = new JLabel(getDialogTitle());
        lblTitle.setFont(UIConstants.FONT_TITLE.deriveFont(Font.BOLD, 16f));
        lblTitle.setForeground(HEADER_FG);

        JButton btnClose = buildCloseButton();

        header.add(lblTitle, BorderLayout.CENTER);
        header.add(btnClose, BorderLayout.EAST);

        // Allow dragging the dialog by its header
        MouseAdapter drag = buildDragAdapter();
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);

        return header;
    }

    private JButton buildCloseButton() {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(255, 255, 255, 40));
                    g2.fillOval(2, 2, getWidth() - 4, getHeight() - 4);
                }
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int m = 10;
                g2.drawLine(m, m, getWidth() - m, getHeight() - m);
                g2.drawLine(getWidth() - m, m, m, getHeight() - m);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> onCancel());
        return btn;
    }

    // ── Footer ───────────────────────────────────────────────────────────────

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(FOOTER_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, FOOTER_BORDER),
            BorderFactory.createEmptyBorder(0, 0, 0, 8)));
        footer.setPreferredSize(new Dimension(0, FOOTER_H));

        RoundedButton btnCancel = RoundedButton.outline(getCancelLabel());
        btnCancel.setPreferredSize(new Dimension(90, UIConstants.BTN_HEIGHT));
        btnCancel.addActionListener(e -> onCancel());

        btnSave = new RoundedButton(getSaveLabel());
        btnSave.setPreferredSize(new Dimension(110, UIConstants.BTN_HEIGHT));
        btnSave.addActionListener(e -> onSave());

        footer.add(btnCancel);
        footer.add(btnSave);
        return footer;
    }

    // ── Keyboard ─────────────────────────────────────────────────────────────

    private void installKeyboardShortcuts() {
        JPanel root = (JPanel) getContentPane();
        InputMap  im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        am.put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onCancel(); }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "save");
        am.put("save", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                // Skip ENTER in text areas / combos with popup open
                Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                                        .getFocusOwner();
                if (focused instanceof JTextArea) return;
                if (focused instanceof JComboBox<?> cb && cb.isPopupVisible()) return;
                onSave();
            }
        });
    }

    // ── Drag to move ─────────────────────────────────────────────────────────

    private MouseAdapter buildDragAdapter() {
        return new MouseAdapter() {
            private Point origin;
            @Override public void mousePressed (MouseEvent e) { origin = e.getPoint(); }
            @Override public void mouseDragged (MouseEvent e) {
                if (origin == null) return;
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - origin.x,
                            loc.y + e.getY() - origin.y);
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Close and dispose the dialog. */
    protected final void close() { dispose(); }

    /** Enable / disable the Save button (e.g., during async save). */
    protected void setSaveEnabled(boolean enabled) {
        if (btnSave != null) btnSave.setEnabled(enabled);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FormBuilder — helper for 2-column rows
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Builds a {@link JPanel} using GridBagLayout with label-input rows.
     *
     * <p>Usage:
     * <pre>
     *   FormBuilder fb = new FormBuilder();
     *   fb.addRow("Tên:", tfName);
     *   fb.addRow("Loại:", cmbCategory, "Vui lòng chọn loại");  // optional error label
     *   return fb.getPanel();
     * </pre>
     */
    public static class FormBuilder {

        private final JPanel panel;
        private final GridBagConstraints gbcLabel;
        private final GridBagConstraints gbcField;
        private int row = 0;

        public FormBuilder() {
            panel = new JPanel(new GridBagLayout());
            panel.setBackground(Color.WHITE);

            gbcLabel = new GridBagConstraints();
            gbcLabel.gridx   = 0;
            gbcLabel.fill    = GridBagConstraints.NONE;
            gbcLabel.anchor  = GridBagConstraints.NORTHWEST;
            gbcLabel.insets  = new Insets(10, 0, 0, 12);
            gbcLabel.weightx = 0;

            gbcField = new GridBagConstraints();
            gbcField.gridx   = 1;
            gbcField.fill    = GridBagConstraints.HORIZONTAL;
            gbcField.anchor  = GridBagConstraints.NORTHWEST;
            gbcField.weightx = 1;
            gbcField.insets  = new Insets(6, 0, 0, 0);
        }

        /**
         * Add a label + input field row.
         * Appends an invisible error label below the field automatically.
         *
         * @param label     row label text (colon appended automatically)
         * @param field     any JComponent (AppTextField, AppComboBox, etc.)
         * @return the created error JLabel (wire it to field.attachErrorLabel)
         */
        public JLabel addRow(String label, JComponent field) {
            return addRow(label, field, 34);
        }

        /** Variant with custom field height (e.g., 120 for image preview). */
        public JLabel addRow(String label, JComponent field, int fieldHeight) {
            // Label
            gbcLabel.gridy = row;
            JLabel lbl = new JLabel(label);
            lbl.setFont(UIConstants.FONT_BOLD);
            lbl.setForeground(UIConstants.TEXT_PRIMARY);
            lbl.setPreferredSize(new Dimension(110, 32));
            panel.add(lbl, gbcLabel);

            // Field + error wrapper
            gbcField.gridy = row;
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setBackground(Color.WHITE);

            GridBagConstraints wc = new GridBagConstraints();
            wc.gridx = 0; wc.gridy = 0;
            wc.fill = GridBagConstraints.HORIZONTAL;
            wc.weightx = 1;
            field.setPreferredSize(new Dimension(0, fieldHeight));
            wrapper.add(field, wc);

            // Error label (hidden until setError called)
            JLabel errLbl = new JLabel();
            errLbl.setFont(UIConstants.FONT_BODY.deriveFont(Font.PLAIN, 11.5f));
            errLbl.setForeground(new Color(0xEF4444));
            errLbl.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
            errLbl.setVisible(false);
            wc.gridy = 1;
            wc.insets = new Insets(1, 0, 0, 0);
            wrapper.add(errLbl, wc);

            panel.add(wrapper, gbcField);
            row++;
            return errLbl;
        }

        /** Add a free-height row (no fixed preferredSize on the field). */
        public JLabel addFreeRow(String label, JComponent field) {
            gbcLabel.gridy  = row;
            gbcLabel.insets = new Insets(14, 0, 0, 12); // top-align with preview
            JLabel lbl = new JLabel(label);
            lbl.setFont(UIConstants.FONT_BOLD);
            lbl.setForeground(UIConstants.TEXT_PRIMARY);
            lbl.setPreferredSize(new Dimension(110, 32));
            panel.add(lbl, gbcLabel);
            gbcLabel.insets = new Insets(10, 0, 0, 12); // reset

            gbcField.gridy = row;
            panel.add(field, gbcField);

            JLabel dummy = new JLabel();
            dummy.setVisible(false);
            row++;
            return dummy;
        }

        /** Add a full-width component spanning both columns. */
        public void addSeparator(JComponent comp) {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0; gc.gridy = row;
            gc.gridwidth = 2;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;
            gc.insets = new Insets(10, 0, 6, 0);
            panel.add(comp, gc);
            row++;
        }

        public JPanel getPanel() { return panel; }
    }
}