package com.zenread.book.presentation.feature.main

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import com.zenread.book.R
import com.zenread.book.databinding.ActivityMainBinding
import com.zenread.book.presentation.feature.book.BookFragment

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar.customToolbar)

        // Handle drawer open with custom icon
        binding.toolbar.iconMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Set navigation item listener
        binding.navView.setNavigationItemSelectedListener(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment, BookFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                finish()
            }
        }
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