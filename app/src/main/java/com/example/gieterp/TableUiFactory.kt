package com.example.gieterp

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat

object TableUiFactory {
    private const val CELL_HORIZONTAL_PADDING = 16
    private const val CELL_VERTICAL_PADDING = 12
    private const val CELL_MARGIN = 4

    fun createHeaderCell(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.table_header_text))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(CELL_HORIZONTAL_PADDING, CELL_VERTICAL_PADDING, CELL_HORIZONTAL_PADDING, CELL_VERTICAL_PADDING)
            background = ContextCompat.getDrawable(context, R.drawable.bg_table_header_cell)
            layoutParams = tableCellLayoutParams()
        }
    }

    fun createBodyCell(context: Context, value: String, isAlert: Boolean = false): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(ContextCompat.getColor(context, R.color.table_cell_text))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(CELL_HORIZONTAL_PADDING, CELL_VERTICAL_PADDING, CELL_HORIZONTAL_PADDING, CELL_VERTICAL_PADDING)
            val backgroundResource = if (isAlert) R.drawable.bg_table_alert_cell else R.drawable.bg_table_body_cell
            setBackgroundResource(backgroundResource)
            layoutParams = tableCellLayoutParams()
        }
    }

    fun createActionButton(context: Context, text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_secondary_button)
            setPadding(CELL_HORIZONTAL_PADDING, CELL_VERTICAL_PADDING, CELL_HORIZONTAL_PADDING, CELL_VERTICAL_PADDING)
            layoutParams = tableCellLayoutParams()
            setOnClickListener { onClick() }
        }
    }

    private fun tableCellLayoutParams(): TableRow.LayoutParams {
        return TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT,
        ).apply {
            setMargins(CELL_MARGIN, CELL_MARGIN, CELL_MARGIN, CELL_MARGIN)
        }
    }
}
