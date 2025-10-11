package com.example.fittrack.ui.analysis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.fittrack.AnalysisPagerAdapter
import com.example.fittrack.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class AnalysisFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_analysis, container, false)

        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        val adapter = AnalysisPagerAdapter(this)
        viewPager.adapter = adapter

        // Attach tabs with accessible content descriptions
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "Progress"
                    tab.contentDescription = "Progress Tab"
                }
                1 -> {
                    tab.text = "Trends"
                    tab.contentDescription = "Trends Tab"
                }
                2 -> {
                    tab.text = "Goals"
                    tab.contentDescription = "Goals Tab"
                }
            }
        }.attach()

        return view
    }
}
