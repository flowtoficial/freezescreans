package freezekit.capitaldascestas

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object UiSupport {
    data class Page(val scroll: ScrollView, val body: LinearLayout)

    fun page(context: Context, title: String, subtitle: String? = null): Page {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 24), dp(context, 22), dp(context, 28))
            setBackgroundColor(Color.rgb(246, 248, 247))
        }
        body.addView(label(context, title, 24f, bold = true), matchWrap(context, bottom = 8))
        if (!subtitle.isNullOrBlank()) {
            body.addView(label(context, subtitle, 15f), matchWrap(context, bottom = 16))
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setOnApplyWindowInsetsListener { view, insets ->
                val safeInsets = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom)
                insets
            }
            addView(body)
        }
        return Page(scroll, body)
    }

    fun label(context: Context, text: CharSequence, sizeSp: Float = 16f, bold: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(Color.rgb(29, 43, 36))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
        }

    fun button(context: Context, text: String, onClick: () -> Unit): Button =
        Button(context).apply {
            this.text = text
            isAllCaps = false
            minHeight = dp(context, 48)
            setOnClickListener { onClick() }
        }

    fun input(context: Context, hint: String, inputType: Int, maxLength: Int? = null): EditText =
        EditText(context).apply {
            this.hint = hint
            this.inputType = inputType
            setSingleLine(true)
            if (maxLength != null) filters = arrayOf(InputFilter.LengthFilter(maxLength))
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        }

    fun gap(context: Context, heightDp: Int = 10): View =
        View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(context, heightDp)) }

    fun matchWrap(context: Context, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, bottom) }

    fun addLabel(body: LinearLayout, context: Context, text: String, sizeSp: Float = 16f, bold: Boolean = false) {
        body.addView(label(context, text, sizeSp, bold), matchWrap(context, 8))
    }

    fun addButton(body: LinearLayout, context: Context, text: String, onClick: () -> Unit): Button {
        val button = button(context, text, onClick)
        body.addView(button, matchWrap(context, 8))
        return button
    }

    fun addInput(
        body: LinearLayout,
        context: Context,
        hint: String,
        inputType: Int,
        maxLength: Int? = null
    ): EditText {
        val field = input(context, hint, inputType, maxLength)
        body.addView(field, matchWrap(context, 8))
        return field
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
