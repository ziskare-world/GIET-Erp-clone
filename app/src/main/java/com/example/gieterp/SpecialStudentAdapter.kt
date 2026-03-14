package com.example.gieterp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class SpecialStudentAdapter(
    private val context: Context,
    initialItems: List<SpecialStudent>,
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val items = initialItems.toMutableList()

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): SpecialStudent = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    fun submitList(newItems: List<SpecialStudent>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        val view: View

        if (convertView == null) {
            view = inflater.inflate(R.layout.item_special_student, parent, false)
            holder = ViewHolder(
                studentNameText = view.findViewById(R.id.studentNameText),
                rollNoText = view.findViewById(R.id.rollNoText),
                registrationNoText = view.findViewById(R.id.registrationNoText),
                programBranchText = view.findViewById(R.id.programBranchText),
                semesterSectionText = view.findViewById(R.id.semesterSectionText),
                contactText = view.findViewById(R.id.contactText),
                hostelInfoText = view.findViewById(R.id.hostelInfoText),
            )
            view.tag = holder
        } else {
            view = convertView
            holder = convertView.tag as ViewHolder
        }

        val student = getItem(position)
        holder.studentNameText.text = student.studentName
        holder.rollNoText.text = context.getString(R.string.label_roll_no, student.rollNo)
        holder.registrationNoText.text = context.getString(R.string.label_registration_no, student.registrationNo)
        holder.programBranchText.text = context.getString(
            R.string.label_program_branch,
            student.program,
            student.branch,
        )
        holder.semesterSectionText.text = context.getString(
            R.string.label_sem_section,
            student.semester,
            student.section,
        )
        holder.contactText.text = context.getString(R.string.label_contact, student.contactNo)
        holder.hostelInfoText.text = context.getString(R.string.label_hostel_info, student.hostelInfo)

        return view
    }

    private data class ViewHolder(
        val studentNameText: TextView,
        val rollNoText: TextView,
        val registrationNoText: TextView,
        val programBranchText: TextView,
        val semesterSectionText: TextView,
        val contactText: TextView,
        val hostelInfoText: TextView,
    )
}
