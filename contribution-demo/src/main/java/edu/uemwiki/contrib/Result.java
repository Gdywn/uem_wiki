package edu.uemwiki.contrib;

public class Result<T> {
    public boolean success;
    public String message;
    public T data;
    private Result(boolean success,String message,T data){this.success=success;this.message=message;this.data=data;}
    public static <T> Result<T> ok(String message,T data){return new Result<T>(true,message,data);}
    public static <T> Result<T> fail(String message){return new Result<T>(false,message,null);}
}
