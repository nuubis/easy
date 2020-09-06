package core.exception

import com.fasterxml.jackson.annotation.JsonProperty


enum class ReqError(val errorCodeStr: String) {
    ENTITY_WITH_ID_NOT_FOUND("ENTITY_WITH_ID_NOT_FOUND"),
    ROLE_NOT_ALLOWED("ROLE_NOT_ALLOWED"),
    ASSESSMENT_AWAIT_TIMEOUT("ASSESSMENT_AWAIT_TIMEOUT"),
    INVALID_PARAMETER_VALUE("INVALID_PARAMETER_VALUE"),
    MOODLE_LINKING_ERROR("MOODLE_LINKING_ERROR"),
    MOODLE_EMPTY_RESPONSE("MOODLE_EMPTY_RESPONSE"),
    MOODLE_GRADE_SYNC_ERROR("MOODLE_GRADE_SYNC_ERROR"),
    ACCOUNT_EMAIL_NOT_FOUND("ACCOUNT_EMAIL_NOT_FOUND"),
    ARTICLE_NOT_FOUND("ARTICLE_NOT_FOUND"),
    ARTICLE_ALIAS_IN_USE("ARTICLE_ALIAS_IN_USE"),
    NO_COURSE_ACCESS("NO_COURSE_ACCESS"),
    NO_GROUP_ACCESS("NO_GROUP_ACCESS"),
    EXERCISE_NOT_AUTOASSESSABLE("EXERCISE_NOT_AUTOASSESSABLE"),
    NO_EXERCISE_ACCESS("NO_EXERCISE_ACCESS"),
    GROUP_NOT_EMPTY("GROUP_NOT_EMPTY"),
}


data class RequestErrorResponse(
        @JsonProperty("id", required = true) val id: String,
        @JsonProperty("code", required = true) val code: String?,
        @JsonProperty("attrs", required = true) val attrs: Map<String, String>,
        @JsonProperty("log_msg", required = true) val logMsg: String
)
