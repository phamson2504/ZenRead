package com.zenread.book.presentation.pdf.popup.reader

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.zenread.book.databinding.LayoutPopupTocBinding
import com.zenread.book.databinding.LayoutReaderMenuBinding
import com.zenread.book.domain.model.SearchTextResult
import com.zenread.book.domain.model.TocItem
import com.zenread.book.presentation.pdf.popup.bookmark.BookmarkAdapter
import com.zenread.book.presentation.pdf.popup.catalogue.CatalogueAdapter
import com.zenread.book.presentation.pdf.popup.search.PdfSearchAdapter

class PopupReaderMenu(val context: Context, parent: ViewGroup) {
    val binding = LayoutReaderMenuBinding.inflate(LayoutInflater.from(context), parent, false)
    val view = binding.root

    private var listener: OnReaderMenuListener? = null
    private var isMenuVisible = false
    private var adapterSearch: PdfSearchAdapter? = null

    private var tocList: List<TocItem> = emptyList()

    fun getTocList(): List<TocItem> {
        if (tocList.isEmpty()) {
            tocList = listener?.getCatalogue() ?: emptyList()
            listener?.setChapterFromTocItem()
        }
        return tocList
    }


    fun setOnTextActionListener(listener: OnReaderMenuListener) {
        this.listener = listener
    }

    init {
        binding.root.post {
            resetMenuPosition()
            getTocList()
        }

    }

    fun toggleMenu() {
        if (isMenuVisible) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    fun showMenu(duration: Long = 200) {
        binding.topMenu.visibility = View.VISIBLE
        binding.bottomMenu.visibility = View.VISIBLE

        binding.topMenu.animate()
            .translationY(0f)
            .setDuration(duration)
            .start()

        binding.bottomMenu.animate()
            .translationY(0f)
            .setDuration(duration)
            .start()

        isMenuVisible = true

        binding.btnVolume.setOnClickListener {
            listener?.onClickVolumeUp()
        }

        binding.btnListBulleted.setOnClickListener {
            val popupTocBinding = LayoutPopupTocBinding.inflate(LayoutInflater.from(context))
            val popupWindow = PopupWindow(
                popupTocBinding.root,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            popupTocBinding.tabLayout.addTab(popupTocBinding.tabLayout.newTab().setText("Chapters"))
            popupTocBinding.tabLayout.addTab(
                popupTocBinding.tabLayout.newTab().setText("Bookmarks")
            )
            popupTocBinding.tabLayout.addTab(
                popupTocBinding.tabLayout.newTab().setText("Highlight")
            )

            popupTocBinding.recyclerView.layoutManager = LinearLayoutManager(context)
            val adapterToc = CatalogueAdapter(tocList) { page ->
                popupWindow.dismiss()
                listener?.moveToPageClicked(page - 1)
            }
            popupTocBinding.recyclerView.adapter = adapterToc

            val listHighlight = listener?.getListHighlight() ?: emptyList()
            val adapterHighlight = BookmarkAdapter(listHighlight) { bookmark ->
                popupWindow.dismiss()
                listener?.moveToHighlight(bookmark.page, bookmark.firstRect)
            }

            popupTocBinding.tabLayout.addOnTabSelectedListener(object :
                TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val adapter = when (tab.position) {
                        0 -> adapterToc
                        2 -> adapterHighlight
                        else -> null
                    }
                    adapter?.let { adapter -> popupTocBinding.recyclerView.adapter = adapter }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })

            popupWindow.showAsDropDown(it)
        }

        binding.btnSearch.setOnClickListener {
            binding.searchView.visibility = View.VISIBLE
            binding.searchDropdownRecycler.visibility = View.VISIBLE
        }

        binding.icBackBtn.setOnClickListener {
            binding.searchView.visibility = View.GONE
            listener?.search("")
            binding.searchInput.setText(null)
            binding.searchInput.hideKeyboard()
        }

        binding.searchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val inputText = binding.searchInput.text.toString()
                listener?.search(inputText)
                adapterSearch = PdfSearchAdapter { result ->
                    listener?.moveToSearchedPosition(result)
                    binding.searchDropdownRecycler.visibility = View.GONE
                    binding.searchView.visibility = View.GONE
                    binding.searchInput.hideKeyboard()
                }
                binding.searchDropdownRecycler.adapter = adapterSearch
                binding.searchDropdownRecycler.layoutManager = LinearLayoutManager(context)
                adapterSearch?.setCurrentKeyword(inputText)
                true
            } else false
        }
    }

    fun setTitle(titleName: String) {
        binding.titleMp.text = titleName
    }

    fun setChapter(chapterName: String) {
        binding.chapter.text = chapterName
    }

    fun View.hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    fun updateSearchItem(itemList: List<SearchTextResult>) {
        adapterSearch?.submitList(itemList)
    }

    fun hideMenu() {
        binding.topMenu.animate()
            .translationY(-binding.topMenu.height.toFloat())
            .setDuration(200)
            .withEndAction { binding.topMenu.visibility = View.INVISIBLE }
            .start()

        binding.bottomMenu.animate()
            .translationY(binding.bottomMenu.height.toFloat())
            .setDuration(200)
            .withEndAction { binding.bottomMenu.visibility = View.INVISIBLE }
            .start()

        binding.searchInput.hideKeyboard()
        isMenuVisible = false
    }

    fun isVisible(): Boolean = isMenuVisible

    private fun resetMenuPosition() {
        binding.topMenu.translationY = -binding.topMenu.height.toFloat()
        binding.bottomMenu.translationY = binding.bottomMenu.height.toFloat()
        binding.topMenu.visibility = View.INVISIBLE
        binding.bottomMenu.visibility = View.INVISIBLE
    }
}