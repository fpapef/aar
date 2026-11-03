package sn.aar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import sn.aar.core.Verdict;

/** Fabrique de vues : toute l'interface est construite en code, avec un theme sombre. */
public final class Ui {

    public static final int BG = 0xFF0C1110;
    public static final int SURFACE = 0xFF141B19;
    public static final int SURFACE2 = 0xFF1F2825;
    public static final int OUTLINE = 0xFF2B3632;
    public static final int TEXT = 0xFFF1F5F3;
    public static final int TEXT2 = 0xFFA3AFAA;
    public static final int TEXT3 = 0xFF6C7873;
    public static final int ACCENT = 0xFF2BD47D;
    public static final int ON_ACCENT = 0xFF03140B;
    public static final int ACCENT_SOFT = 0x262BD47D;
    public static final int ORANGE = 0xFFF5A524;
    public static final int ORANGE_SOFT = 0x26F5A524;
    public static final int RED = 0xFFF05252;
    public static final int RED_SOFT = 0x26F05252;

    private Ui() {}

    public static int couleur(int niveau) {
        return niveau == Verdict.ARNAQUE ? RED : niveau == Verdict.SUSPECT ? ORANGE : ACCENT;
    }

    public static int couleurDouce(int niveau) {
        return niveau == Verdict.ARNAQUE ? RED_SOFT : niveau == Verdict.SUSPECT ? ORANGE_SOFT : ACCENT_SOFT;
    }

    public static int icone(int niveau) {
        return niveau == Verdict.ARNAQUE ? R.drawable.ic_error : niveau == Verdict.SUSPECT ? R.drawable.ic_warning : R.drawable.ic_check_circle;
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable roundedStroke(int fill, int stroke, int strokePx, float radiusPx) {
        GradientDrawable d = rounded(fill, radiusPx);
        d.setStroke(strokePx, stroke);
        return d;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public static Drawable ripple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, int l, int t, int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, 28, TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setLetterSpacing(-0.01f);
        return t;
    }

    public static TextView heading(Context c, CharSequence s) {
        TextView t = text(c, s, 18, TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView bold(Context c, CharSequence s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    public static TextView body(Context c, CharSequence s) {
        return text(c, s, 15, TEXT2);
    }

    public static TextView caption(Context c, CharSequence s) {
        return text(c, s, 13, TEXT3);
    }

    /** Titre de section, avec un peu d'air au-dessus. */
    public static TextView section(Context c, CharSequence s) {
        TextView t = heading(c, s);
        t.setLayoutParams(margins(matchWrap(), c, 4, 10, 4, 10));
        return t;
    }

    /** Carte arrondie. */
    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(rounded(SURFACE, dp(c, 22)));
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        l.setLayoutParams(margins(matchWrap(), c, 0, 0, 0, 14));
        return l;
    }

    public static TextView button(Context c, CharSequence label, boolean primary) {
        TextView b = text(c, label, 16, primary ? ON_ACCENT : TEXT);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        int ph = dp(c, 18), pv = dp(c, 14);
        b.setPadding(ph, pv, ph, pv);
        b.setMinHeight(dp(c, 52));
        b.setBackground(ripple(rounded(primary ? ACCENT : SURFACE2, dp(c, 16)), primary ? 0x55FFFFFF : 0x33FFFFFF));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static TextView smallButton(Context c, CharSequence label) {
        TextView b = text(c, label, 14, TEXT);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        int ph = dp(c, 14), pv = dp(c, 10);
        b.setPadding(ph, pv, ph, pv);
        b.setBackground(ripple(rounded(SURFACE2, dp(c, 12)), 0x33FFFFFF));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static TextView lien(Context c, CharSequence label) {
        TextView b = text(c, label, 14, ACCENT);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int p = dp(c, 6);
        b.setPadding(p, p, p, p);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static EditText champ(Context c, CharSequence hint, boolean multiligne) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(TEXT3);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setBackground(roundedStroke(SURFACE2, OUTLINE, dp(c, 1), dp(c, 14)));
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        if (multiligne) {
            e.setMinLines(4);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            e.setSingleLine(true);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        }
        return e;
    }

    public static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    /** Pastille ronde avec une icone au centre. */
    public static FrameLayout pastille(Context c, int res, int bg, int fg, int sizeDp, int iconDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(oval(bg));
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(fg));
        f.addView(iv, new FrameLayout.LayoutParams(dp(c, iconDp), dp(c, iconDp), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return f;
    }

    /** Bouton rond avec une icone (retour, etc.). */
    public static FrameLayout iconButton(Context c, int res, int sizeDp) {
        FrameLayout f = pastille(c, res, SURFACE2, TEXT, sizeDp, 22);
        f.setBackground(ripple(oval(SURFACE2), 0x44FFFFFF));
        f.setClickable(true);
        f.setFocusable(true);
        return f;
    }

    /** Barre de risque de 0 a 100. */
    public static View jauge(Context c, int score, int color) {
        LinearLayout l = hbox(c);
        l.setBackground(rounded(SURFACE2, dp(c, 4)));
        l.setClipToOutline(true);
        View plein = new View(c);
        plein.setBackground(rounded(color, dp(c, 4)));
        l.addView(plein, new LinearLayout.LayoutParams(0, dp(c, 8), Math.max(score, 2)));
        View vide = new View(c);
        l.addView(vide, new LinearLayout.LayoutParams(0, dp(c, 8), Math.max(100 - score, 0)));
        l.setLayoutParams(margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 8)), c, 0, 12, 0, 4));
        return l;
    }

    /** Ligne a puce. */
    public static LinearLayout puce(Context c, CharSequence s, int couleurPuce) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        View point = new View(c);
        point.setBackground(oval(couleurPuce));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(c, 7), dp(c, 7));
        pp.setMargins(0, dp(c, 8), dp(c, 12), 0);
        l.addView(point, pp);
        TextView t = body(c, s);
        t.setTextColor(TEXT);
        l.addView(t, weight(1));
        l.setLayoutParams(margins(matchWrap(), c, 0, 4, 0, 4));
        return l;
    }

    public static TextView extrait(Context c, String s, int lignes) {
        TextView t = body(c, s);
        t.setMaxLines(lignes);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }
}
