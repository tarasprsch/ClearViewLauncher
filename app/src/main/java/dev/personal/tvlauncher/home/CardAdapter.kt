package dev.personal.tvlauncher.home

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

data class CardEntry(
    val key: String,
    val title: String,
    val artwork: String? = null,
    val drawable: Drawable? = null,
    val logo: Boolean = false,
    val ratio: Float = 16f / 9f,
    val presentation: CardPresentation = CardPresentation.CONTENT,
    val open: () -> Unit,
)

class CardAdapter(
    private val entries: List<CardEntry>,
    private val focused: (Int, String) -> Unit,
    private val vertical: (Int) -> Boolean,
    private val confineHorizontalFocus: Boolean = false,
) : RecyclerView.Adapter<CardAdapter.Holder>() {
    class Holder(val card: ContentCard) : RecyclerView.ViewHolder(card)
    override fun getItemCount(): Int = entries.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ContentCard(parent.context))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        holder.card.apply {
            bind(entry.title, entry.artwork, entry.drawable, entry.logo, entry.ratio, entry.presentation)
            tag = "card:${entry.key}"
            setOnClickListener { entry.open() }
            setOnLongClickListener(null)
            isLongClickable = false
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) focused(position, entry.key)
            }
            setOnKeyListener { _, key, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (key) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> vertical(-1)
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> vertical(1)
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> confineHorizontalFocus && position == 0
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> confineHorizontalFocus && position == entries.lastIndex
                        else -> false
                    }
                } else false
            }
        }
    }
    override fun onViewRecycled(holder: Holder) { holder.card.recycle() }
}
