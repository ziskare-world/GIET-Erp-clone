package com.example.gieterp

data class BranchOption(
    val id: Int,
    val label: String,
    val shortLabel: String,
)

object SpecialDirectoryConfig {
    const val PROGRAM_ID = 1
    const val SEMESTER = 6

    val branchOptions = listOf(
        BranchOption(0, "All Branches", "All"),
        BranchOption(1, "Computer Science and Engineering", "CSE"),
        BranchOption(2, "Computer Science and Technology", "CST"),
        BranchOption(3, "CSE in AI and ML", "AI & ML"),
        BranchOption(4, "CSE in Data Science", "Data Science"),
        BranchOption(5, "CSE in IoT", "IoT"),
        BranchOption(7, "Civil Engineering", "Civil"),
        BranchOption(8, "Electronics and Communications Engineering", "ECE"),
        BranchOption(9, "Mechanical Engineering", "Mechanical"),
        BranchOption(10, "Chemical Engineering", "Chemical"),
        BranchOption(11, "Biotechnology", "Biotech"),
        BranchOption(12, "Electrical Engineering", "EE"),
        BranchOption(13, "Electrical and Electronics Engineering", "EEE"),
        BranchOption(42, "BSHK", "BSHK"),
        BranchOption(63, "Electronics Engineering (VLSI)", "VLSI"),
        BranchOption(77, "Hotel Management", "HM"),
        BranchOption(78, "Aeronautical Engineering", "Aero"),
    )

    val branchLabelById = branchOptions.associate { option ->
        option.id to option.label
    }

    val branchShortLabelById = branchOptions.associate { option ->
        option.id to option.shortLabel
    }
}
