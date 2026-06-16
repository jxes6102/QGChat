package training.QGChat.auth.exception;

// API 錯誤碼固定給前端判斷流程；中文 message 則給使用者直接閱讀。
public final class ErrorCode {
    public static final String CANNOT_CREATE_DIRECT_WITH_SELF = "CANNOT_CREATE_DIRECT_WITH_SELF";
    public static final String CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND";
    public static final String DISPLAY_NAME_BLANK = "DISPLAY_NAME_BLANK";
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    public static final String EMAIL_BLANK = "EMAIL_BLANK";
    public static final String GROUP_MEMBER_NOT_FOUND = "GROUP_MEMBER_NOT_FOUND";
    public static final String GROUP_NOT_FOUND = "GROUP_NOT_FOUND";
    public static final String GROUP_OWNER_REQUIRED = "GROUP_OWNER_REQUIRED";
    public static final String INVALID_ACCOUNT_OR_PASSWORD = "INVALID_ACCOUNT_OR_PASSWORD";
    public static final String INVALID_CURRENT_PASSWORD = "INVALID_CURRENT_PASSWORD";
    public static final String INVALID_RESET_TOKEN = "INVALID_RESET_TOKEN";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";
    public static final String METADATA_INVALID_JSON = "METADATA_INVALID_JSON";
    public static final String MISSING_TOKEN = "MISSING_TOKEN";
    public static final String NEW_OWNER_MUST_BE_ANOTHER_MEMBER = "NEW_OWNER_MUST_BE_ANOTHER_MEMBER";
    public static final String NO_PROFILE_FIELDS = "NO_PROFILE_FIELDS";
    public static final String NOT_ACTIVE_GROUP_MEMBER = "NOT_ACTIVE_GROUP_MEMBER";
    public static final String NOT_CONVERSATION_PARTICIPANT = "NOT_CONVERSATION_PARTICIPANT";
    public static final String NOT_GROUP_MEMBER = "NOT_GROUP_MEMBER";
    public static final String REPLY_MESSAGE_WRONG_CONVERSATION = "REPLY_MESSAGE_WRONG_CONVERSATION";
    public static final String TARGET_USER_NOT_FOUND = "TARGET_USER_NOT_FOUND";
    public static final String TRANSFER_OWNER_BEFORE_LEAVING = "TRANSFER_OWNER_BEFORE_LEAVING";
    public static final String UNSUPPORTED_MESSAGE_TYPE = "UNSUPPORTED_MESSAGE_TYPE";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USERNAME_ALREADY_EXISTS = "USERNAME_ALREADY_EXISTS";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String GROUP_ADD_MEMBERS_REQUIRED = "GROUP_ADD_MEMBERS_REQUIRED";
    public static final String GROUP_REMOVE_MEMBERS_REQUIRED = "GROUP_REMOVE_MEMBERS_REQUIRED";
    public static final String CANNOT_REMOVE_GROUP_MEMBER = "CANNOT_REMOVE_GROUP_MEMBER";
    public static final String USE_OWNER_TRANSFER = "USE_OWNER_TRANSFER";

    private ErrorCode() {
    }
}
