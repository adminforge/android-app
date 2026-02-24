package de.adminforge.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.InputStream

class ChangelogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val textView = findViewById<TextView>(R.id.changelog_text)
        val markwon = io.noties.markwon.Markwon.create(this)
        
        try {
            assets.open("CHANGELOG.md").bufferedReader().use { reader ->
                val text = reader.readText()
                markwon.setMarkdown(textView, text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            textView.text = "Changelog konnte nicht geladen werden."
        }
    }
}
