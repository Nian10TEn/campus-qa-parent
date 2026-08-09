package com.campus.common;

import lombok.Data;

@Data
public class R<T> {
    private int code;
    private String message;
    private T data;
    private R() {}

    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.code = 200;
        r.message = "操作成功";
        return r;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = ok();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> error(String message) {
        return fail(500, message);
    }
}