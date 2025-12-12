package com.cashbk.app.ui.business

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.cashbk.app.data.model.Notebook
import com.cashbk.app.databinding.ActivityBusinessDetailBinding
import com.cashbk.app.databinding.ItemNotebookBinding
import com.cashbk.app.ui.notebook.NotebookActivity
import com.google.firebase.database.*

class BusinessDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBusinessDetailBinding
    private lateinit var database: DatabaseReference
    private lateinit var notebookAdapter: NotebookAdapter
    private val notebookList = mutableListOf<Notebook>()
    private var businessId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBusinessDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        businessId = intent.getStringExtra("businessId")
        Log.d("BusinessDetailActivity", "Received businessId: $businessId")

        if (businessId.isNullOrEmpty()) {
            Toast.makeText(this, "Business ID is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("notebooks")

        setupRecyclerView()
        fetchNotebooks()

        binding.addNotebookFab.setOnClickListener {
            Toast.makeText(this, "Add new notebook clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        notebookAdapter = NotebookAdapter(notebookList) { notebook ->
            Log.d("BusinessDetailActivity", "Clicked notebook: '${notebook.name}', ID: '${notebook.id}'")

            if (notebook.id.isEmpty()) {
                Toast.makeText(this, "Error: Notebook ID is empty! Cannot open.", Toast.LENGTH_LONG).show()
                return@NotebookAdapter
            }

            val intent = Intent(this, NotebookActivity::class.java)
            intent.putExtra("notebookId", notebook.id)
            startActivity(intent)
        }

        binding.notebooksRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@BusinessDetailActivity)
            adapter = notebookAdapter
        }
    }

    private fun fetchNotebooks() {
        Log.d("BusinessDetailActivity", "Querying notebooks where businessId == $businessId")

        database.orderByChild("businessId").equalTo(businessId)
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    notebookList.clear()

                    if (snapshot.exists()) {
                        Log.d("BusinessDetailActivity", "Found ${snapshot.childrenCount} notebooks.")

                        for (notebookSnapshot in snapshot.children) {
                            val notebook = notebookSnapshot.getValue(Notebook::class.java)
                            notebook?.id = notebookSnapshot.key.orEmpty()
                            notebook?.let { notebookList.add(it) }
                        }

                    } else {
                        Log.d("BusinessDetailActivity", "No results for businessId: $businessId")
                        Toast.makeText(this@BusinessDetailActivity, "No notebooks found.", Toast.LENGTH_SHORT).show()
                    }

                    notebookAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("BusinessDetailActivity", "Error: ${error.message}")
                    Toast.makeText(this@BusinessDetailActivity, "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // -------------------------------------------------------------
    // ADAPTER USING VIEWBINDING
    // -------------------------------------------------------------
    inner class NotebookAdapter(
        private val notebooks: List<Notebook>,
        private val onClick: (Notebook) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<NotebookAdapter.NotebookViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): NotebookViewHolder {
            val itemBinding = ItemNotebookBinding.inflate(layoutInflater, parent, false)
            return NotebookViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: NotebookViewHolder, position: Int) {
            val notebook = notebooks[position]
            holder.bind(notebook)
            holder.itemView.setOnClickListener { onClick(notebook) }
        }

        override fun getItemCount() = notebooks.size

        inner class NotebookViewHolder(private val itemBinding: ItemNotebookBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(notebook: Notebook) {
                itemBinding.notebookName.text = notebook.name
            }
        }
    }
}
