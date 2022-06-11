package com.luban;

public class BasicValue implements Value {

    protected Value next = null;

    
    @Override
    public Value getNext() {
        return next;
    }

    public void invoke(String handling) {
        handling = handling.replaceAll("aa", "bbb");
        System.out.println("基础阀门处理完后:" + handling);
    }

    public void setNext(Value value) {
        this.next = value;
    }
}
