package com.erise.ai.backend.common.exception;

public final class ErrorCodes {

    public static final int BAD_REQUEST = 400000;
    public static final int UNAUTHORIZED = 401000;
    public static final int FORBIDDEN = 403000;
    public static final int NOT_FOUND = 404000;
    public static final int CONFLICT = 409000;
    public static final int FILE_ERROR = 510000;
    public static final int AI_ERROR = 520000;
    public static final int SEARCH_ERROR = 530000;
    public static final int SERVER_ERROR = 500000;

    private ErrorCodes() {
    }
}
