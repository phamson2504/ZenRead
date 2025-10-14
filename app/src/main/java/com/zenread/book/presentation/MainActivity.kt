package com.zenread.book.presentation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import com.google.android.material.navigation.NavigationView
import com.zenread.book.R
import com.zenread.book.core.base.BaseActivity
import com.zenread.book.databinding.ActivityMainBinding
import com.zenread.book.presentation.book.ui.BookFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(),
    NavigationView.OnNavigationItemSelectedListener {

    override fun inflateBinding(layoutInflater: LayoutInflater) =
        ActivityMainBinding.inflate(layoutInflater)

    override fun setupView(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set up toolbar
        setSupportActionBar(binding.toolbar.customToolbar)

        // Handle drawer open with custom icon
        binding.toolbar.iconMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Set navigation item listener
        binding.navView.setNavigationItemSelectedListener(this)

        if (savedInstanceState == null) {
            val defaultItem = binding.navView.menu.findItem(R.id.nav_recent_books)
            defaultItem.isChecked = true
            onNavigationItemSelected(defaultItem)
        }

        //setup optionMenu
        setupOptionMenu()

        onBackPressedDispatcher.addCallback(this) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                finish()
            }
        }
    }

    private fun setupOptionMenu() {
        val popupMenu = PopupMenu(this, binding.toolbar.icOptionMenu).apply {
            menuInflater.inflate(R.menu.op_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.om_add_file -> {
                        true
                    }

                    else -> false
                }
            }
        }

        binding.toolbar.icOptionMenu.setOnClickListener {
            popupMenu.show()
        }
    }

    fun setToolbarTitle(title: String) {
        binding.toolbar.tbTitle.text = title
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_recent_books -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.main_fragment, BookFragment())
                    .commit()
            }

            R.id.nav_bookshelf -> {
                Toast.makeText(this, "Bookshelf clicked", Toast.LENGTH_SHORT).show()
            }

            R.id.nav_settings -> {
                Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}