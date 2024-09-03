package com.example.uploadbackground.adapter

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.uploadbackground.R
import com.example.uploadbackground.model.FileModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class FileAdapter(
    private var files: MutableList<FileModel>,
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.bind(file)

        val isUploaded = getUploadedFiles().contains(file.fileName)
        holder.status.setCardBackgroundColor(
            context.resources.getColor(if (isUploaded) R.color.status_true else R.color.status_false)
        )
    }
    fun updateFiles(newFiles: List<FileModel>) {
        files = newFiles.toMutableList()
        notifyDataSetChanged()
    }

    private fun getUploadedFiles(): Set<String> {
        val json = sharedPreferences.getString("uploaded_files", null)
        return json?.let {
            Gson().fromJson(it, object : TypeToken<Set<String>>() {}.type)
        } ?: emptySet()
    }

    override fun getItemCount() = files.size
    fun getFiles(): List<FileModel> = files
    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewFileName: TextView = itemView.findViewById(R.id.textViewFileName)
        private val textViewDate: TextView = itemView.findViewById(R.id.textViewDate)
        private val textViewTime: TextView = itemView.findViewById(R.id.textViewTime)
        var status: CardView = itemView.findViewById(R.id.status)

        fun bind(file: FileModel) {
            textViewFileName.text = file.fileName
            textViewDate.text = file.displayDate
            textViewTime.text = file.displayTime
        }
    }
}
