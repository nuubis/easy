package components.modal

enum class Modal {
    CREATE_COURSE,

    CREATE_EXERCISE,
    CREATE_DIR,
    ADD_EXERCISE_TO_COURSE,

    ADD_STUDENTS_TO_COURSE,
    ADD_TEACHERS_TO_COURSE,
    REMOVE_STUDENTS_FROM_COURSE,
    REMOVE_TEACHERS_FROM_COURSE,
    CREATE_COURSE_GROUP,
    DELETE_COURSE_GROUP,
    ADD_STUDENTS_TO_COURSE_GROUP,
    ADD_TEACHERS_TO_COURSE_GROUP,
    REMOVE_STUDENTS_FROM_COURSE_GROUP,
    REMOVE_TEACHERS_FROM_COURSE_GROUP,

    CODE_EDITOR_NEW_FILE,

    REMOVE_EXERCISE_FROM_COURSE,
    REORDER_COURSE_EXERCISES,
    UPDATE_COURSE_EXERCISE_TITLE_ALIAS,
    DIR_PERMISSIONS,
}