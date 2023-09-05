package translation


object EngStrings : TranslatableStrings() {

    override val otherLanguage = "Eesti keeles"
    override val notFoundPageTitle = "Page not found"
    override val notFoundPageMsg = "Nothing to see here :("
    override val noPermissionForPageMsg = "It seems like you have no permission to look here. :("
    override val noCourseAccessPageMsg = "It seems like you have no access to this course. :("
    override val somethingWentWrong =
        "Something went wrong... Try to refresh the page and if it doesn't get any better, please contact an administrator."
    override val yes = "Yes"
    override val no = "No"
    override val myCourses = "My courses"
    override val exerciseLibrary = "Exercise library"
    override val gradedAutomatically = "Graded automatically"
    override val gradedByTeacher = "Graded by teacher"
    override val notGradedYet = "Not graded"
    override val closeToggleLink = "▼ Close"
    override val doSave = "Save"
    override val saving = "Saving..."
    override val doAdd = "Add"
    override val adding = "Adding..."
    override val cancel = "Cancel"
    override val solutionCodeTabName = "lahendus"
    override val solutionEditorPlaceholder = "Write, paste or drag your solution here..."
    override val exerciseSingular = "exercise"
    override val exercisePlural = "exercises"
    override val doEditTitle = "Edit title"
    override val doMove = "Move"
    override val moving = "Moving..."
    override val doDelete = "Delete"
    override val deleted = "Deleted"
    override val doRestore = "Restore"
    override val doChange = "Change"

    override val permissionP = "Passthrough"
    override val permissionPR = "Viewer"
    override val permissionPRA = "Adder"
    override val permissionPRAW = "Editor"
    override val permissionPRAWM = "Moderator"

    override val roleAdmin = "Admin"
    override val roleTeacher = "Teacher"
    override val roleStudent = "Student"
    override val accountData = "Account settings"
    override val logOut = "Log out"

    override val newExercise = "New exercise"
    override val newCourse = "New course"
    override val allExercises = "All exercises"
    override val exercises = "Exercises"
    override val participants = "Participants"
    override val gradesLabel = "Grades"
    override val courseSettings = "Course settings"
    override val linkAbout = "About Lahendus"
    override val linkTOS = "Terms"

    override val coursesTitle = "My courses"
    override val coursesTitleAdmin = "All courses"
    override val studentsSingular = "student"
    override val studentsPlural = "students"
    override val enrolledOnCourseAttrKey = "Enrolled"
    override val coursesSingular = "course"
    override val coursesPlural = "courses"
    override val completedBadgeLabel = "Completed!"

    override val deadlineLabel = "Deadline"
    override val completedLabel = "Completed"
    override val startedLabel = "Unsuccessful"
    override val ungradedLabel = "Ungraded"
    override val unstartedLabel = "Not submitted"

    override val tabExerciseLabel = "Exercise"
    override val tabTestingLabel = "Testing"
    override val tabSubmissionsLabel = "Submissions"
    override val tabSubmit = "Submit"
    override val tabAllSubmissions = "All submissions"
    override val draftSaveFailedMsg = "Saving the draft failed"
    override val exerciseClosedForSubmissions = "This exercise is closed and does not allow any new submissions"
    override val solutionEditorStatusDraft = "Unsubmitted draft"
    override val solutionEditorStatusSubmission = "Latest submission"
    override val submissionSingular = "submission"
    override val submissionPlural = "submissions"
    override val softDeadlineLabel = "Deadline"
    override val hardDeadlineLabel = "Closing time"
    override val graderTypeLabel = "Grading"
    override val thresholdLabel = "Threshold"
    override val studentVisibleLabel = "Visible to students"
    override val studentVisibleFromTimeLabel = "Visible from"
    override val assStudentVisibleLabel = "Assessments visible to students"
    override val lastModifiedLabel = "Last modified"
    override val graderTypeAuto = "automatic"
    override val graderTypeTeacher = "manual"
    override val autoAssessmentLabel = "Automatic tests"
    override val teacherAssessmentLabel = "Teacher feedback"
    override val gradeLabel = "Points"
    override val doAutoAssess = "Check"
    override val autoAssessing = "Checking..."
    override val tryAgain = "Try again"
    override val addAssessmentLink = "► Add assessment"
    override val addAssessmentGradeLabel = "Grade (0-100)"
    override val addAssessmentFeedbackLabel = "Feedback"
    override val addAssessmentGradeValidErr = "The grade has to be an integer between 0 and 100."
    override val addAssessmentButtonLabel = "Add assessment"
    override val assessmentAddedMsg = "Assessment added"
    override val submissionTimeLabel = "Submission time"
    override val doSubmitAndCheck = "Submit and check"
    override val doSubmit = "Submit"
    override val submissionHeading = "Submission"
    override val latestSubmissionSuffix = "(latest submission)"
    override val allSubmissionsLink = "► View all submissions"
    override val loadingAllSubmissions = "Loading submissions..."
    override val oldSubmissionNote = "This is an old submission."
    override val toLatestSubmissionLink = "View the latest submission."
    override val aaTitle = "Automatic tests"
    override val submitSuccessMsg = "Solution submitted"
    override val autogradeException = "There was an exception during the program's execution:"
    override val autogradeFailedMsg = """

         ¯\_(ツ)_/¯
           
Automatic testing failed.
Someone has probably already been notified 
of the issue, please try again later.
        """
    override val autogradeCreatedFiles = "Before running the program, the following files were created:"
    override val autogradeStdIn = "Inputs provided to the program:"
    override val autogradeStdOut = "The program's full output:"
    override val autogradeNoChecksInTest= "There weren't any checks to run. I guess that means we're fine?"

    override val testType = "Test type"

    override val aboutS1="Lahendus is operated and developed by the"
    override val aboutS2="Institute of Computer Science at the University of Tartu"
    override val aboutS3="Lahendus is based on an open-source application called"
    override val aboutS4=" which is also developed at the Institute of Computer Science"
    override val aboutS5="If you have any questions about Lahendus or are interested in its development, or if you found a bug, then come talk to us in"
    override val aboutS6="our Discord server"
    override val aboutSponsors="The development of Lahendus and easy, and creating some of the exercises has been supported by"
    override val statsAutograding="Submissions being autograded"
    override val statsSubmissions="Total submissions"
    override val statsAccounts="Total accounts"

    override val today = "today"
    override val yesterday = "yesterday"
    override val tomorrow = "tomorrow"
    override val monthList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
}