/*
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.podplay.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.raywenderlich.podplay.R
import com.raywenderlich.podplay.adapter.PodcastListAdapter
import com.raywenderlich.podplay.databinding.ActivityPodcastBinding
import com.raywenderlich.podplay.databinding.SearchItemBinding
import com.raywenderlich.podplay.repository.ItunesRepo
import com.raywenderlich.podplay.repository.PodcastRepo
import com.raywenderlich.podplay.service.FeedService
import com.raywenderlich.podplay.service.ItunesService
import com.raywenderlich.podplay.viewmodel.PodcastViewModel
import com.raywenderlich.podplay.viewmodel.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PodcastActivity : AppCompatActivity(), PodcastListAdapter.PodcastListAdapterListener {

  private val TAG = javaClass.simpleName
  private lateinit var binding: ActivityPodcastBinding
  private val searchViewModel by viewModels<SearchViewModel>()
  private lateinit var podcastListAdapter: PodcastListAdapter
  private lateinit var searchMenuItem: MenuItem
  private val podcastViewModel by viewModels<PodcastViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityPodcastBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val itunesService = ItunesService.instance
    val itunesRepo = ItunesRepo(itunesService)

    GlobalScope.launch {
    }
    setupToolbar()
    setupViewModels()
    updateControls()
    handleIntent(intent)
    addBackStackListener()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater = menuInflater
    inflater.inflate(R.menu.menu_search, menu)
    searchMenuItem = menu.findItem(R.id.search_item)
    val searchView = searchMenuItem.actionView as SearchView
    val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager

    searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
    if (supportFragmentManager.backStackEntryCount > 0) {
      binding.podcastRecyclerView.visibility = View.INVISIBLE
    }
    if (binding.podcastRecyclerView.visibility == View.INVISIBLE) {
      searchMenuItem.isVisible = false
    }
    return true
  }

  private fun performSearch(term: String) {
    showProgressBar()
    GlobalScope.launch {
      val results = searchViewModel.searchPodcasts(term)
      withContext(Dispatchers.Main) {
        hideProgressBar()
        binding.toolbar.title = term
        podcastListAdapter.setSearchData(results)
      }
    }
  }

  private fun handleIntent(intent: Intent) {
    if (Intent.ACTION_SEARCH == intent.action) {
      val query = intent.getStringExtra(SearchManager.QUERY) ?:
      return
      performSearch(query)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun setupToolbar() {
    setSupportActionBar(binding.toolbar)
  }

  private fun setupViewModels() {
    val service = ItunesService.instance
    searchViewModel.iTunesRepo = ItunesRepo(service)
    podcastViewModel.podcastRepo = PodcastRepo(FeedService.instance)
  }

  private fun updateControls() {
    binding.podcastRecyclerView.setHasFixedSize(true)
    val layoutManager = LinearLayoutManager(this)
    binding.podcastRecyclerView.layoutManager = layoutManager
    val dividerItemDecoration = DividerItemDecoration(
      binding.podcastRecyclerView.context,
      layoutManager.orientation)

    binding.podcastRecyclerView.addItemDecoration(dividerItemDecoration)
    podcastListAdapter = PodcastListAdapter(null, this, this)
    binding.podcastRecyclerView.adapter = podcastListAdapter
  }

  override fun onShowDetails(podcastSummaryViewData:
                             SearchViewModel.PodcastSummaryViewData) {
    podcastSummaryViewData.feedUrl?.let {
      showProgressBar()
      podcastViewModel.getPodcast(podcastSummaryViewData)
    }
  }

  private fun createSubscription() {
    podcastViewModel.podcastLiveData.observe(this, {
      hideProgressBar()
      if (it != null) {
        showDetailsFragment()
      } else {
        showError("Error loading feed")
      }
    })
  }

  private fun createPodcastDetailsFragment():
          PodcastDetailsFragment {
    var podcastDetailsFragment = supportFragmentManager
      .findFragmentByTag(TAG_DETAILS_FRAGMENT) as
            PodcastDetailsFragment?
    if (podcastDetailsFragment == null) {
      podcastDetailsFragment =
        PodcastDetailsFragment.newInstance()
    }
    return podcastDetailsFragment
  }

  private fun showDetailsFragment() {
    // 1
    val podcastDetailsFragment = createPodcastDetailsFragment()
    // 2
    supportFragmentManager.beginTransaction().add(
      R.id.podcastDetailsContainer,
      podcastDetailsFragment, TAG_DETAILS_FRAGMENT)
      .addToBackStack("DetailsFragment").commit()
    // 3
    binding.podcastRecyclerView.visibility = View.INVISIBLE
    // 4
    searchMenuItem.isVisible = false
  }

  private fun addBackStackListener() {
    supportFragmentManager.addOnBackStackChangedListener {
      if (supportFragmentManager.backStackEntryCount == 0) {
        binding.podcastRecyclerView.visibility = View.VISIBLE
      }
    }
  }

  private fun showProgressBar() {
    binding.progressBar.visibility = View.VISIBLE
  }
  private fun hideProgressBar() {
    binding.progressBar.visibility = View.INVISIBLE
  }

  private fun showError(message: String) {
    AlertDialog.Builder(this)
      .setMessage(message)
      .setPositiveButton(getString(R.string.ok_button), null)
      .create()
      .show()
  }

  companion object {
    private const val TAG_DETAILS_FRAGMENT = "DetailsFragment"
  }

}
